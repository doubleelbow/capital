(ns com.doubleelbow.capital.interceptor.impl.alpha.init-context
  (:require [com.doubleelbow.capital.interceptor.alpha :as interceptor]))

(defn interceptor [config ks]
  {::interceptor/name ::init-context
   ::interceptor/init (reduce #(assoc %1 %2 (%2 config))
                              {}
                              ks)})

