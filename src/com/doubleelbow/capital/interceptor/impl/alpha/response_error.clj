(ns com.doubleelbow.capital.interceptor.impl.alpha.response-error
  (:require [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [io.pedestal.log :as log]))

(defn types [error]
  (::response-error-type (ex-data error) #{}))

(defn add-type [error type]
  (let [data (ex-data error)
        old-type (::response-error-type data #{})]
   (ex-info (.getMessage error)
            (assoc data ::response-error-type (conj old-type type)))))

(defn- contains-type? [error type]
  (log/debug :msg "check type of response error" :types (::response-error-type (ex-data error)))
  (contains? (types error) type))

(defn transient? [error]
  (contains-type? error ::transient))

(defn retriable? [error]
  (log/debug :msg "check if error retriable")
  (contains-type? error ::retriable))

(defn interceptor [error-fn & fns]
  {::interceptor/name ::response-error
   ::interceptor/error (fn [context error]
                         (assoc context ::capital/error (reduce #(apply %2 [context %1])
                                                                error
                                                                (concat [error-fn] fns))))})

