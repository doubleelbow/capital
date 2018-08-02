(ns com.doubleelbow.capital.alpha
  (:require [clojure.core.async :as async]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [io.pedestal.log :as log]))

(defn- channel? [c] (instance? clojure.core.async.impl.protocols.Channel c))

(defn- finished? [ctx] (and (empty? (::queue ctx)) (empty? (::stack ctx))))

(defn- throwable->ex-info [^Throwable t interceptor stage]
  (let [iname (::interceptor/name interceptor)
        throwable-str (pr-str (type t))]
    (ex-info (str throwable-str " in Interceptor " iname " - " (.getMessage t))
             (merge {:stage stage
                     :interceptor iname
                     :exception-type (keyword throwable-str)
                     :exception t}
                    (ex-data t))
             t)))

(defn- try-f [context interceptor stage]
  (if-let [f (interceptor/get-stage-fn interceptor stage)]
    (try
      (if (= stage ::interceptor/error)
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
        (try-f current-intc ::interceptor/up))))

(defn- execute-stack [context stage]
  (let [current-intc (first (::stack context))
        new-stack (next (::stack context))]
    (-> context
        (assoc ::stack new-stack)
        (try-f current-intc stage))))

(defn- step-through! [context c]
  (let [context (cond
                  (::error context) (execute-stack context ::interceptor/error)
                  (not (empty? (::queue context))) (execute-queue context)
                  :else (execute-stack context ::interceptor/down))]
    (cond
      (channel? context) (async/take! context
                                (fn [ctx]
                                  (step-through! ctx c)))
      (finished? context) (async/put! c (::response context))
      :else (step-through! context c))))

(defn- merge-init-context [ctx init-map name]
  (reduce (fn [c [k v]]
            (if-let [x (get c k)]
              (do
                (log/debug :msg (str "cannot add key " k " of interceptor " name " - key already exists"))
                c)
              (assoc c k v)))
          ctx
          init-map))

(defn- create-init-context [ctx interceptors]
  (reduce #(if (interceptor/interceptor? %2)
             (merge-init-context %1 (::interceptor/init %2 {}) (::interceptor/name %2 "unknown"))
             (do
               (log/debug :msg (str %2 " is not an interceptor"))
               %1))
          ctx
          interceptors))

(defn initial-context [type name interceptors]
  (-> {}
      (assoc ::type type)
      (assoc ::name name)
      (assoc ::interceptors interceptors)
      (create-init-context interceptors)))

(defn <send!
  [request context]
  (let [c (async/chan)
        ctx (-> context
                (assoc ::request request)
                (assoc ::queue (::interceptors context))
                (assoc ::stack '()))]
    (do
      (step-through! ctx c)
      c)))
