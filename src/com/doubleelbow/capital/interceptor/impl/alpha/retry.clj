(ns com.doubleelbow.capital.interceptor.impl.alpha.retry
  (:require [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [com.doubleelbow.capital.interceptor.impl.alpha.response-error :as response-error]
            [clojure.core.async :refer [<!] :as async]
            [io.pedestal.log :as log]))

(defn- config
  ([context k]
   (config context k nil))
  ([context k default]
   (k (deref (::retry-config context)) default)))

(defn- data [context k]
  (get-in context [::retry-data k]))

(defn- retry? [context error]
  (let [retriable? (config context ::retriable-fn #(response-error/transient? %2))]
    (and (< (data context ::counter) (config context ::max-retries))
         (retriable? context error))))

(defn- delay-timeout [delays delay-type counter]
  (let [[firstd secondd] delays]
    (case counter
      1 firstd
      2 secondd
      (if (= ::linear delay-type)
        (+ firstd (* (- secondd firstd) (dec counter)))
        (* firstd (Math/pow (/ secondd firstd) (dec counter)))))))

(def ^:private delay-intc
  {::interceptor/name ::retry-delay
   ::interceptor/up (fn [context]
                      (let [counter (data context ::counter)]
                        (if (> counter 0)
                          (let [dt (delay-timeout (config context ::delay) (config context ::delay-type) counter)]
                            (log/debug :msg "delaying before sending request" :delay dt)
                            (async/go
                              (<! (async/timeout dt))
                              context))
                          context)))})

(defn interceptor [config]
  {::interceptor/name ::retry
   ::interceptor/init {::retry-config (atom config)}
   ::interceptor/up (fn [context]
                      (if (contains? context ::retry-data)
                        (do
                          (log/debug :msg "retry request" :counter (data context ::counter))
                          context)
                        (let [queue (concat [delay-intc] (::capital/queue context))
                              current-intc (first (::capital/stack context))]
                          (-> context
                              (assoc ::capital/queue queue)
                              (assoc ::retry-data {::queue (concat [current-intc] queue)
                                                   ::counter 0})))))
   ::interceptor/error (fn [context error]
                         (if (retry? context error)
                           (-> context
                               (update-in [::retry-data ::counter] inc)
                               (assoc ::capital/queue (get-in context [::retry-data ::queue])))
                           (assoc context ::capital/error error)))})
