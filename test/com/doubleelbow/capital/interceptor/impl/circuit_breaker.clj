(ns com.doubleelbow.capital.interceptor.impl.circuit-breaker
  (:require [com.doubleelbow.capital.interceptor.impl.alpha.circuit-breaker :as sut]
            [com.doubleelbow.capital.interceptor.impl.alpha.time :as intc.time]
            [com.doubleelbow.capital.interceptor.impl.alpha.response-error :as intc.resp-error]
            [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as intc]
            [com.doubleelbow.capital.interceptor.impl.alpha :as impl]
            [clojure.test :as t]
            [clj-time.core :as time]))

(defn- initial-time []
  (time/today-at (rand-int 24) (rand-int 60) (rand-int 60)))

(defn- create-context [cb-config cb-stats time-fn]
  (merge (impl/set-config cb-config ::sut/circuit-breaker-config)
         {::sut/circuit-breaker-stats {::sut/requests (atom (:requests cb-stats []))
                                       ::sut/state (atom {::sut/state-type (:type cb-stats ::sut/closed)
                                                          ::sut/last-change (:last-change cb-stats)})}
          ::intc.time/current-time-fn time-fn}))

(defn- stats [context]
  (let [reqs (deref (get-in context [::sut/circuit-breaker-stats ::sut/requests]))
        state (deref (get-in context [::sut/circuit-breaker-stats ::sut/state]))]
    {:requests reqs
     :type (::sut/state-type state)
     :last-change (::sut/last-change state)}))

(defn- make-transient [error]
  (intc.resp-error/add-type error ::intc.resp-error/transient))

(defn- exec [fn-key & args]
  (let [interceptor (sut/interceptor {})]
    (apply (fn-key interceptor) args)))

(defn- exec-before [context]
  (exec ::sut/before context))

(defn- exec-after [context]
  (exec ::sut/after context))

(defn- exec-error [context error]
  (exec ::intc/error context error))

(t/deftest when-cb-closed-before-returns-context
  (let [ctx (create-context {} nil nil)
        ctx-stats (stats ctx)
        new-ctx (exec-before ctx)]
    (t/is (= ctx-stats (stats new-ctx)))))

(t/deftest when-cb-opened-and-last-change-not-exceeded-before-throws
  (let [current-time (initial-time)
        last-change (time/minus current-time (time/seconds (rand-int 60)))
        current-time-fn (fn [context] current-time)
        ctx (create-context {::sut/open-duration 60} {:type ::sut/opened :last-change last-change} current-time-fn)
        expected-exc-data {:type ::sut/circuit-breaker :cause ::sut/opened}]
    (try
      (exec-before ctx)
      (t/is (= false "should not be here"))
      (catch clojure.lang.ExceptionInfo e (t/is (= expected-exc-data (ex-data e)))))))

(t/deftest when-cb-opened-and-last-change-exceeded-trips-to-half-opened
  (let [current-time (initial-time)
        last-change (time/minus current-time (time/seconds (+ 60 (rand-int 60))))
        current-time-fn (fn [context] current-time)
        ctx (create-context {::sut/open-duration 60} {:type ::sut/opened :last-change last-change} current-time-fn)
        new-ctx (exec-before ctx)]
    (t/is (= {:requests [] :type ::sut/half-opened :last-change current-time} (stats new-ctx)))))

(t/deftest successful-response-adds-to-requests-and-leaves-cb-closed
  (let [current-time (initial-time)
        current-time-fn (fn [context] current-time)
        ctx (create-context {::sut/interval 300} {:type ::sut/closed} current-time-fn)
        new-ctx (exec-after ctx)]
    (t/is (= {:requests [{:date current-time :transient? false}] :type ::sut/closed :last-change nil}
             (stats new-ctx)))))

(t/deftest successful-response-adds-to-requests-and-trips-from-half-opened-to-closed
  (let [current-time (initial-time)
        current-time-fn (fn [context] current-time)
        ctx (create-context {::sut/interval 300} {:type ::sut/half-opened} current-time-fn)
        new-ctx (exec-after ctx)]
    (t/is (= {:requests [{:date current-time :transient? false}] :type ::sut/closed :last-change current-time}
             (stats new-ctx)))))

(t/deftest successful-response-removes-requests-from-more-than-interval-seconds-ago
  (let [current-time (initial-time)
        current-time-fn (fn [context] current-time)
        last-response-time (time/minus current-time (time/seconds (+ 300 (rand-int 1000))))
        ctx (create-context {::sut/interval 300} {:type ::sut/closed :requests [{:date last-response-time :transient? false}]} current-time-fn)
        ctx-stats (stats ctx)
        new-ctx (exec-after ctx)]
    (t/is (= [{:date current-time :transient? false}] (:requests (stats new-ctx))))))

(t/deftest successful-response-does-not-remove-requests-from-less-than-interval-seconds-ago
  (let [current-time (initial-time)
        current-time-fn (fn [context] current-time)
        last-response-time (time/minus current-time (time/seconds (rand-int 300)))
        ctx (create-context {::sut/interval 300} {:type ::sut/closed :requests [{:date last-response-time :transient? false}]} current-time-fn)
        ctx-stats (stats ctx)
        new-ctx (exec-after ctx)]
    (t/is (= [{:date last-response-time :transient? false} {:date current-time :transient? false}]
             (:requests (stats new-ctx))))))

(t/deftest successful-response-leaves-context-unchanged-if-cb-opened
  (let [ctx (create-context {} {:type ::sut/opened} nil)
        ctx-stats (stats ctx)
        new-ctx (exec-after ctx)]
    (t/is (= ctx-stats (stats new-ctx)))))

(t/deftest error-response-leaves-stats-unchanged-if-cb-opened
  (let [ctx (create-context {} {:type ::sut/opened} nil)
        ctx-stats (stats ctx)
        new-ctx (exec-error ctx (ex-info "fake error" {:resp "error"}))]
    (t/is (= ctx-stats (stats new-ctx)))
    (t/is (contains? new-ctx ::capital/error))))

(t/deftest non-transient-error-cannot-trip-cb
  (let [current-time (initial-time)
        current-time-fn (fn [context] current-time)
        cb-config {::sut/interval 300 ::sut/treshold 1 ::sut/min-requests 2}
        last-response-time (time/minus current-time (time/seconds (rand-int 300)))
        ctx (create-context cb-config {:type ::sut/closed :requests [{:date last-response-time :transient? true}]} current-time-fn)
        ctx-stats (stats ctx)
        new-ctx (exec-error ctx (ex-info "non-transient error" {:resp "error"}))
        new-stats (stats new-ctx)]
    (t/is (= [{:date last-response-time :transient? true} {:date current-time :transient? false}]
             (:requests new-stats)))
    (t/is (= (:type ctx-stats) (:type new-stats)))
    (t/is (= (:last-change ctx-stats) (:last-change new-stats)))))

(t/deftest transient-error-can-trip-cb
  (let [current-time (initial-time)
        current-time-fn (fn [context] current-time)
        cb-config {::sut/interval 300 ::sut/threshold 1 ::sut/min-requests 2}
        last-response-time (time/minus current-time (time/seconds (rand-int 300)))
        ctx (create-context cb-config {:type ::sut/closed :requests [{:date last-response-time :transient? true}]} current-time-fn)
        ctx-stats (stats ctx)
        new-ctx (exec-error ctx (make-transient (ex-info "transient error" {:resp "error"})))
        new-stats (stats new-ctx)]
    (t/is (= [] (:requests new-stats)))
    (t/is (= ::sut/opened (:type new-stats)))
    (t/is (= current-time (:last-change new-stats)))))

(t/deftest transient-error-after-interval-does-not-trip-cb
  (let [current-time (initial-time)
        current-time-fn (fn [context] current-time)
        cb-config {::sut/interval 300 ::sut/threshold 1 ::sut/min-requests 2}
        last-response-time (time/minus current-time (time/seconds (+ 300 (rand-int 1000))))
        ctx (create-context cb-config {:type ::sut/closed :requests [{:date last-response-time :transient? true}]} current-time-fn)
        ctx-stats (stats ctx)
        new-ctx (exec-error ctx (make-transient (ex-info "transient error" {:resp "error"})))
        new-stats (stats new-ctx)]
    (t/is (= [{:date current-time :transient? true}] (:requests new-stats)))
    (t/is (= (:type ctx-stats) (:type new-stats)))
    (t/is (= (:last-change ctx-stats) (:last-change new-stats)))))

