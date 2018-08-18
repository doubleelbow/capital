(ns com.doubleelbow.capital.interceptor.impl.response-error
  (:require [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [io.pedestal.log :as log]))

(defn assoc-data [error key val & kvs]
  (ex-info (.getMessage error)
           (apply assoc (concat [(ex-data error) key val] kvs))))

(defn transient? [error]
  (= ::transient (::capital/exception-type (ex-data error))))

(defn interceptor [error-fn & fns]
  {::interceptor/name ::response-error
   ::interceptor/error (fn [context error]
                         (assoc context ::capital/error (reduce #(apply %2 [context %1])
                                                                error
                                                                (concat [error-fn] fns))))})

