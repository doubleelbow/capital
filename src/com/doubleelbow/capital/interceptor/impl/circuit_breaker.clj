(ns com.doubleelbow.capital.interceptor.impl.circuit-breaker
  (:require [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [com.doubleelbow.capital.interceptor.impl.time :as time-intc]
            [io.pedestal.log :as log]
            [clj-time.core :as time]))

(defn- stat [context k]
  (get-in context [::circuit-breaker-stats k]))

(defn- config [context k]
  (let [conf (::circuit-breaker-config context)]
    (k @conf)))

(defn- current-state [context]
  (::state-type (deref (stat context ::state))))

(defn- opened? [context]
  (= ::opened (current-state context)))

(defn- half-opened? [context]
  (= ::half-opened (current-state context)))

(defn- opened-time-exceeded? [context]
  (let [opened-from (::last-change (deref (stat context ::state)))
        max-open-duration (config context ::open-duration)]
    (time/after? (time-intc/current-time context)
                 (time/plus opened-from (time/seconds max-open-duration)))))

(defn- transient? [error]
  (= ::transient (::capital/exception-type (ex-data error))))

(defn- add! [context reqs transient?]
  (let [r {:date (time-intc/current-time context)
           :transient? transient?}]
    (log/debug :msg "adding req info to requests" :req r)
    (swap! reqs conj r)))

(defn- remove-old! [context reqs]
  (swap! reqs (fn [rs min-allowed-time]
                (log/debug :msg "removing old requests" :before min-allowed-time)
                (remove #(time/before? (:date %) min-allowed-time) rs))
         (time/minus (time-intc/current-time context) (time/seconds (config context ::interval)))))

(defn- clear-requests! [context]
  (reset! (stat context ::requests) []))

(defn- repopulate! [context transient?]
  (log/debug :msg "repopulating" :transient transient?)
  (let [reqs (stat context ::requests)]
    (add! context reqs transient?)
    (remove-old! context reqs)
    context))

(defn- trip! [context to-state]
  (log/info :msg "circuit breaker state changed" :new-state to-state)
  (let [state (stat context ::state)]
    (reset! state {::state-type to-state
                   ::last-change (time-intc/current-time context)})))

(defn- threshold-exceeded? [context]
  (log/debug :msg "circuit breaker threshold-exceeded? fn")
  (let [reqs (deref (stat context ::requests))
        reqs-count (count reqs)]
    (log/debug :msg "current requests count" :count reqs-count)
    (if (< reqs-count (config context ::min-requests))
      false
      (let [threshold (config context ::threshold)
            transient-errors (count (remove #(get :transient? %) reqs))]
        (log/debug :msg "threshold and transient errors count" :threshold threshold :transient-errors transient-errors)
        (if (< threshold 1)
          (>= (/ transient-errors reqs-count) threshold)
          (>= transient-errors threshold))))))

(defn- trip-on-transient-if-necessary! [context]
  (if (or (half-opened? context)
          (threshold-exceeded? context))
    (do
      (log/error :msg "tripping circuit breaker state to opened")
      (trip! context ::opened)
      (clear-requests! context))))

(defn circuit-breaker-intc [config]
  {::interceptor/name ::circuit-breaker
   ::interceptor/dependencies ::time-intc/time
   ::interceptor/aliases [::before ::after]
   ::interceptor/init {::circuit-breaker-config (atom config)
                       ::circuit-breaker-stats {::requests (atom [])
                                                ::state (atom {::state-type ::closed
                                                               ::last-change nil})}}
   ::before (fn [context]
              (cond
                (and (opened? context) (opened-time-exceeded? context)) (trip! context ::half-opened)
                (opened? context) (throw (ex-info "Circuit breaker is opened." {:type ::circuit-breaker :cause ::opened})))
              context)
   ::after (fn [context]
             (log/debug :msg "circuit breaker successful response fn")
             (repopulate! context false)
             (if (half-opened? context)
               (trip! context ::closed))
             context)
   ::interceptor/error (fn [context error]
                         (log/debug :msg "circuit breaker error fn")
                         (if (not (opened? context))
                           (if (transient? error)
                             (do
                               (log/warn :msg "transient error" :request (::capital/request context) :error error)
                               (repopulate! context true)
                               (trip-on-transient-if-necessary! context))
                             (do
                               (repopulate! context false)))
                           (log/debug :msg "skipping circuit breaker error handling because circuit breaker is opened"))
                         (assoc context ::capital/error error))})

