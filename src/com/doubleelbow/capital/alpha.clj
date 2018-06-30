(ns com.doubleelbow.capital.alpha
  (:require [clojure.core.async :refer [chan put! take!]]))

(defprotocol Service
  (prepare-context [service context]))

(defn- channel? [c] (instance? clojure.core.async.impl.protocols.Channel c))

(defn- finished? [ctx] (and (empty? (::queue ctx)) (empty? (::stack ctx))))

(defn- throwable->ex-info [^Throwable t interceptor stage]
  (let [iname (:name interceptor)
        throwable-str (pr-str (type t))]
    (ex-info (str throwable-str " in Interceptor " iname " - " (.getMessage t))
             (merge {:stage stage
                     :interceptor iname
                     :exception-type (keyword throwable-str)
                     :exception t}
                    (ex-data t))
             t)))

(defn- try-f [context interceptor stage]
  (if-let [f (get interceptor stage)]
    (try
      (if (= stage :error)
        (f (dissoc context ::error) (::error context))
        (f context))
      (catch Throwable t
        (assoc context ::error (throwable->ex-info t interceptor stage))))
    context))

(defn- execute-queue [context]
  (let [current-intc (first (::queue context))
        new-queue (next (::queue context))
        new-stack (conj (::stack context) current-intc)]
    (-> context
        (assoc ::queue new-queue
               ::stack new-stack)
        (try-f current-intc :up))))

(defn- execute-stack [context stage]
  (let [current-intc (first (::stack context))
        new-stack (next (::stack context))]
    (-> context
        (assoc ::stack new-stack)
        (try-f current-intc stage))))

(defn- step-through! [context c]
  (let [context (cond
                  (::error context) (execute-stack context :error)
                  (not (empty? (::queue context))) (execute-queue context)
                  :else (execute-stack context :down))]
    (cond
      (channel? context) (take! context
                                (fn [ctx]
                                  (step-through! ctx c)))
      (finished? context) (put! c (::response context))
      :else (step-through! context c))))

(defn <send
  [service request interceptors]
  (let [c (chan)
        context (->> {::request request
                      ::queue interceptors
                      ::stack '()}
                     (prepare-context service))]
    (do
      (step-through! context c)
      c)))
