(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer (javadoc)]
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [com.doubleelbow.capital.alpha :as capital]
   [clojure.core.async :refer [<!! go]]))

(defrecord FakeService []
    capital/Service
  (prepare-context [service context]
    (assoc context :execution-order [])))

(defn printing-intc [print-value]
  (letfn [(echo-intc [v stage]
            (fn [context]
              (do
                (println (str "executing " v " at " stage))
                context)))]
    {:name (keyword print-value)
     :up (echo-intc print-value :up)
     :down (echo-intc print-value :down)}))

(defn async-echo-intc [echo-value]
  (letfn [(echo-intc [v stage]
            (fn [context]
              (go
                (println (str "executing " v " at " stage " inside go block"))
                (update context :execution-order conj (str "executed " v " at " stage)))))]
    {:name (keyword echo-value)
     :up (echo-intc echo-value :up)
     :down (echo-intc echo-value :down)}))

(def fake-request-intc
  {:name :fake-request
   :up (fn [context]
         (do
           (println "Putting Success as response.")
           (-> (update context :execution-order conj "Putting Success as response.")
               (assoc ::capital/response "Success"))))})

(def simple-parsing-intc
  {:name :parser
   :down (fn [context]
           (go
             (assoc context ::capital/response (:execution-order context))))})

(defn interceptors [intc-fn]
  (map #(intc-fn (str "intc" (inc %))) (range 3)))

(comment
  (<!! (capital/<send (->FakeService) {} (conj (vec (interceptors printing-intc)) fake-request-intc)))

  (<!! (capital/<send (->FakeService) {} (conj (vec (concat [simple-parsing-intc] (interceptors async-echo-intc))) fake-request-intc))))
