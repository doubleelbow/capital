(ns com.doubleelbow.capital.interceptor.alpha)

(defn- contains-at-least-one? [m ks]
  (not (empty? (filter #(contains? m %) ks))))

(defn interceptor? [v]
  (if (map? v)
    (let [aliases (::aliases v [::up ::down ::error])]
      (or (contains-at-least-one? v aliases) (contains? v ::init)))
    false))

(defn- stage-alias [interceptor stage]
  (if-let [aliases (::aliases interceptor)]
    (let [i (case stage
              ::up 0
              ::down 1
              ::error 2)]
      (get aliases i (get [::up ::down ::error] i)))
    stage))

(defn stage-fn [interceptor stage]
  (let [stage (stage-alias interceptor stage)]
    (get interceptor stage)))

(defn names [coll]
  (cond
    (nil? coll) []
    (map? coll) [(::name coll)]
    :else (map ::name coll)))
