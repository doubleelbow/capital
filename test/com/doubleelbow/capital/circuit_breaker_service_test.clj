(ns com.doubleelbow.capital.circuit-breaker-service-test
  (:require [com.doubleelbow.capital.alpha :as sut]
            [com.doubleelbow.capital.interceptor.alpha :as intc]
            [com.doubleelbow.capital.interceptor.impl.alpha.time :as intc.time]
            [com.doubleelbow.capital.interceptor.impl.alpha.circuit-breaker :as intc.cb]
            [com.doubleelbow.capital.interceptor.impl.alpha.response-error :as intc.resp-error]
            [clojure.test :as t]
            [clojure.core.async :refer [<!!]]
            [clj-time.core :as time]))

(defn- initial-time []
  (time/today-at (rand-int 24) (rand-int 60) (rand-int 60)))

(defn- context [config]
  (let [interceptors [{::intc/name ::error-handling
                       ::intc/error (fn [context error]
                                      (assoc context ::sut/response {:error? true
                                                                     :data error}))}
                      (intc.time/interceptor #(get-in % [::sut/request :time]))
                      (intc.cb/interceptor (::intc.cb/config config))
                      (intc.resp-error/interceptor (fn [context error]
                                                     (let [data (ex-data error)]
                                                       (if (and (:error? data) (= :transient (:type data)))
                                                         (intc.resp-error/add-type error ::intc.resp-error/transient)
                                                         error))))
                      {::intc/name ::request-intc
                       ::intc/up (fn [context]
                                   (let [response (get-in context [::sut/request :response])]
                                     (if (:error? response)
                                       (throw (ex-info (:msg response "fake exception") response))
                                       (assoc context ::sut/response response))))}]]
    (sut/initial-context :cb-test interceptors)))

(defn- error-response [type msg]
  {:error? true
   :type type
   :msg msg})

(defn- response [msg]
  {:error? false
   :msg msg})

(defn- fake-response [response time]
  {:response response
   :time time})

(defn- cb-stats [context]
  {:requests (deref (get-in context [::intc.cb/circuit-breaker-stats ::intc.cb/requests]))
   :state (deref (get-in context [::intc.cb/circuit-breaker-stats ::intc.cb/state]))})

(defn- send-requests [config responses]
  (let [ctx (context config)]
    (run! #(<!! (sut/<send! % ctx)) responses)
    (cb-stats ctx)))

(defn- fake-responses [responses times]
  (map fake-response responses times))

(defn- responses-spaced-by-seconds [responses init-time seconds]
  (let [times (iterate #(time/plus % (time/seconds seconds)) init-time)]
    (fake-responses responses times)))

(def cb-config {::intc.cb/config {::intc.cb/threshold 10
                                  ::intc.cb/interval 300
                                  ::intc.cb/min-requests 10
                                  ::intc.cb/open-duration 150}})

(t/deftest no-errors-cb-closed
  (let [init-time (initial-time)
        responses (responses-spaced-by-seconds (repeat 100 (response "test")) init-time 1)
        stats (send-requests cb-config responses)]
    (t/is (= (count responses) (count (:requests stats))))
    (t/is (= ::intc.cb/closed (get-in stats [:state ::intc.cb/state-type])))
    (t/is (nil? (get-in stats [:state ::intc.cb/last-change])))))

(t/deftest non-threshold-errors-count-cb-closed
  (let [init-time (initial-time)
        responses (responses-spaced-by-seconds (repeat 9 (error-response :transient "error")) init-time 1)
        stats (send-requests cb-config responses)]
    (t/is (= (count responses) (count (:requests stats))))
    (t/is (= ::intc.cb/closed (get-in stats [:state ::intc.cb/state-type])))
    (t/is (nil? (get-in stats [:state ::intc.cb/last-change])))))

(t/deftest threshold-error-trips-cb-state-to-opened
  (let [init-time (initial-time)
        responses (responses-spaced-by-seconds (repeat 10 (error-response :transient "error")) init-time 1)
        stats (send-requests cb-config responses)]
    (t/is (= 0 (count (:requests stats))))
    (t/is (= ::intc.cb/opened (get-in stats [:state ::intc.cb/state-type])))
    (t/is (time/equal? (:time (last responses)) (get-in stats [:state ::intc.cb/last-change])))))

(t/deftest threshold-reached-outside-interval-cb-closed
  (let [init-time (initial-time)
        responses (-> (responses-spaced-by-seconds (repeat 9 (error-response :transient "error")) init-time 1)
                      (concat [(fake-response (error-response :transient "error") (time/plus init-time (time/seconds 309)))]))
        stats (send-requests cb-config responses)]
    (t/is (= 1 (count (:requests stats))))
    (t/is (= ::intc.cb/closed (get-in stats [:state ::intc.cb/state-type])))
    (t/is (nil? (get-in stats [:state ::intc.cb/last-change])))))
