(ns com.doubleelbow.capital.interceptor.impl.async-decide
  (:require [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [io.pedestal.log :as log]))

(defn async-decide-intc [config]
  {::interceptor/name ::async-decide
   ::interceptor/up (fn [context]
                      (let [intcs-type (if (get-in context [::capital/request ::async?] false)
                                         ::async-intcs
                                         ::sync-intcs)
                            intcs-or-fn (intcs-type config)
                            additional-intcs (if (fn? intcs-or-fn)
                                               (apply intcs-or-fn [context])
                                               intcs-or-fn)]
                        (log/debug :msg "async decide intc" :intcs additional-intcs)
                        (update context ::capital/queue #(if (map? additional-intcs)
                                                           (conj % additional-intcs)
                                                           (concat % additional-intcs)))))})
