(ns com.doubleelbow.capital.interceptor.impl.alpha
  (:require [io.pedestal.log :as log]))

(defn set-config [config k]
  (cond
    (map? config) {k (atom config)}
    (or (instance? clojure.lang.Atom config) (fn? config)) {k config}
    :else (throw (ex-info "config should be an atom or a function" {:config config :type (type config)}))))

(defn- get-config [context config-key]
  (let [config (config-key context)]
    (cond
      (instance? clojure.lang.Atom config) (deref config)
      (fn? config) (config))))

(defn config
  ([context base-key other-keys]
   (config context base-key other-keys nil))
  ([context base-key other-keys default]
   (let [ks (if (keyword? other-keys)
              [other-keys]
              other-keys)
         conf (get-config context base-key)]
     (get-in conf ks default))))
