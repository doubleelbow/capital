(ns com.doubleelbow.capital.interceptor.alpha)

(defn- contains-at-least-one? [m ks]
  (not (empty? (filter #(contains? m %) ks))))

(defn interceptor? [v]
  (if (map? v)
    (let [aliases (::aliases v [::up ::down ::error])]
      (contains-at-least-one? v aliases))
    false))

(defn- get-stage-alias [interceptor stage]
  (if-let [aliases (::aliases interceptor)]
    (let [i (case stage
              ::up 0
              ::down 1
              ::error 2)]
      (get aliases i))
    stage))

(defn get-stage-fn [interceptor stage]
  (let [stage (get-stage-alias interceptor stage)]
    (get interceptor stage)))
