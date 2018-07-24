(ns examples.printing
  (:require [clojure.core.async :refer [go]]
            [com.doubleelbow.capital.alpha :as capital]))

(def ^:private response-handling-intc
  {:name :prepare-context
   :up (fn [context]
         (assoc context :execution-order []))
   :down (fn [context]
           (assoc context ::capital/response (:execution-order context)))})

(defn- self-describing-intc [name print-fn]
  {:name (keyword name)
   :up (print-fn name :up)
   :down (print-fn name :down)})

(defn- sync-print-intc [name]
  (letfn [(print-fn [value stage]
            (fn [context]
              (do
                (println (str "executing " value " at " stage))
                (update context :execution-order conj (str "executed " value " at " stage)))))]
    (self-describing-intc name print-fn)))

(defn- async-print-intc [name]
  (letfn [(print-fn [value stage]
            (fn [context]
              (go
                (println (str "executing " value " at " stage " inside go block"))
                (update context :execution-order conj (str "executed " value " at " stage)))))]
    (self-describing-intc name print-fn)))

(def ^:private request-intc
  {:name :fake-request
   :up (fn [context]
         (do
           (println "Putting Success as response")
           (update context :execution-order conj "Putting Success as response.")))})

(defn- interceptors [intc-fn size]
  (map #(intc-fn (str "intc" (inc %))) (range size)))

(defn- simple-service [intc-fn size]
  (let [intcs (conj (vec (concat [response-handling-intc] (interceptors intc-fn size))) request-intc)]
    (capital/initial-context :simple :example-printing-service intcs)))

(defn sync-service [size]
  (simple-service sync-print-intc size))

(defn async-service [size]
  (simple-service async-print-intc size))
