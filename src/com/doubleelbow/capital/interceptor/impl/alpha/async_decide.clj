(ns com.doubleelbow.capital.interceptor.impl.alpha.async-decide
  (:require [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [io.pedestal.log :as log]))

(defn- additional-intcs-dependencies-present? [context additional-intcs]
  (let [interceptors (interceptor/names (concat (::capital/interceptors context) additional-intcs))]
    (log/debug :msg "async decide dependencies check" :interceptors interceptors :additional-intcs additional-intcs)
    (every? #(capital/dependencies-present? % interceptors) additional-intcs)))

(defn- should-check-dependencies? [context additional-intcs-type]
  (not (additional-intcs-type (deref (::async-decide-checks context)))))

(defn async-decide-intc [config]
  {::interceptor/name ::async-decide
   ::interceptor/init {::async-decide-checks (atom {::async-intcs false
                                                    ::sync-intcs false})}
   ::interceptor/up (fn [context]
                      (let [intcs-type (if (get-in context [::capital/request ::async?] false)
                                         ::async-intcs
                                         ::sync-intcs)
                            intcs-or-fn (intcs-type config)
                            additional-intcs (if (fn? intcs-or-fn)
                                               (apply intcs-or-fn [context])
                                               intcs-or-fn)
                            additional-intcs (if (map? additional-intcs)
                                               [additional-intcs]
                                               additional-intcs)]
                        (if (should-check-dependencies? context intcs-type)
                          (let [deps-present? (additional-intcs-dependencies-present? context additional-intcs)]
                            (log/info :msg "async decide dependencies check" :type intcs-type :success? deps-present?)
                            (swap! (::async-decide-checks context) assoc intcs-type deps-present?)
                            (if (not deps-present?)
                              (throw (ex-info "Service context is not configured properly: Interceptor dependencies not present." {:context context})))))
                        (log/debug :msg "async decide intc" :intcs additional-intcs)
                        (update context ::capital/queue concat additional-intcs)))})
