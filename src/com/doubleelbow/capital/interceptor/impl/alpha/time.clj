(ns com.doubleelbow.capital.interceptor.impl.alpha.time
  (:require [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [clj-time.core :as time]))

(defn current-time [context]
  (apply (::current-time-fn context) [context]))

(defn time-intc
  ([]
   (time-intc (fn [context]
                (time/now))))
  ([time-fn]
   {::interceptor/name ::time
    ::interceptor/init {::current-time-fn time-fn}}))
