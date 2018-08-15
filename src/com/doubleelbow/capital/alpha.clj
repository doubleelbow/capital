(ns com.doubleelbow.capital.alpha
  (:require [clojure.core.async :as async]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [io.pedestal.log :as log]))

(defn- channel? [c] (instance? clojure.core.async.impl.protocols.Channel c))

(defn- finished? [ctx] (and (empty? (::queue ctx)) (empty? (::stack ctx))))

(defn- throwable->ex-info [^Throwable t iname stage]
  (let [throwable-str (pr-str (type t))]
    (ex-info (str throwable-str " in Interceptor " iname " - " (.getMessage t))
             (merge {::stage stage
                     ::name iname
                     ::exception-type (keyword throwable-str)
                     ::exception t}
                    (ex-data t))
             t)))

(defn throw-on-channel [^Throwable t c context interceptor-name stage]
  (async/put! c (assoc context ::error (throwable->ex-info t interceptor-name stage))))

(defn- try-f [context interceptor stage]
  (if-let [f (interceptor/stage-fn interceptor stage)]
    (try
      (if (= stage ::interceptor/error)
        (f (dissoc context ::error) (::error context))
        (f context))
      (catch Throwable t
        (-> context
            (assoc ::queue [])
            (assoc ::error (throwable->ex-info t (::interceptor/name interceptor) stage)))))
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
  (log/debug :msg "stepping through interceptor chain" :queue (interceptor/names (::queue context)) :stack (interceptor/names (::stack context)))
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

(defn- dependent-interceptors-present? [interceptors dependencies]
  (log/debug :msg "dependent interceptors present ?" :interceptors interceptors :dependencies dependencies)
  (let [dependencies (if (keyword? dependencies)
                       [dependencies]
                       dependencies)]
    (every? #(not= -1 (.indexOf interceptors %)) dependencies)))

(defn dependencies-present? [current-intc all-intcs]
  (log/debug :msg "check dependencies" :current current-intc :interceptors all-intcs)
  (dependent-interceptors-present? (take-while #(not= (::interceptor/name current-intc) %) all-intcs)
                                  (::interceptor/dependencies current-intc)))

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
  (reduce #(cond
             (not (interceptor/interceptor? %2)) (throw (ex-info "Not an interceptor." {:value %2}))
             (not (dependencies-present? %2 (interceptor/names (::interceptors %1)))) (throw (ex-info "Could not find all dependent interceptors." {:interceptor (::interceptor/name %2)
                                                                                                                                                    :dependencies (::interceptor/dependencies %2)}))
             :else (merge-init-context %1 (::interceptor/init %2 {}) (::interceptor/name %2 "unknown")))
          ctx
          interceptors))

(defn initial-context [name interceptors]
  (try
    (-> {}
        (assoc ::name name)
        (assoc ::interceptors interceptors)
        (create-init-context interceptors))
    (catch Exception e (do
                         (log/error :msg (.getMessage e) :data (ex-data e))
                         (throw e)))))

(defn <send!
  [request context]
  (let [c (async/chan)
        ctx (-> context
                (assoc ::request request)
                (assoc ::queue (::interceptors context))
                (assoc ::stack '()))]
    (log/debug :msg "sending request over capital service"
               :context (-> context
                            (update ::interceptors interceptor/names)
                            (dissoc ::queue ::stack)))
    (step-through! ctx c)
    c))
