(ns examples.echo
  (:require [clojure.core.async :refer [<!!]]
            [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]))

(def ^:private cache-intc
  {::interceptor/name :cache
   ::interceptor/init {:cache (atom {})}
   ::interceptor/aliases [:before :after]
   :before (fn [ctx]
             (let [text (get-in ctx [::capital/request :text])
                   cache (:cache ctx)]
               (if (contains? @cache text)
                 (do
                   (swap! cache update text inc)
                   (-> ctx
                       (assoc ::capital/response {:text (str text " - cached value")})
                       (assoc ::capital/queue [])
                       (update ::capital/stack rest)))
                 ctx)))
   :after (fn [ctx]
            (if (get-in ctx [::capital/request :cache?])
              (let [text (get-in ctx [::capital/response :text])
                    cache (:cache ctx)]
                (if (not (nil? text))
                  (swap! cache assoc text 0)
                  ctx)
                ctx)
              ctx))})

(def ^:private echo-intc
  {::interceptor/name :echo
   ::interceptor/up (fn [ctx]
                      (let [request (::capital/request ctx)
                            text (:text request)]
                        (if (.contains text "error")
                          (throw (Exception. "error found in response"))
                          (assoc ctx ::capital/response request))))
   ::interceptor/error (fn [ctx e]
                         (do
                           (assoc ctx ::capital/response {:error (ex-data e)})))})

(defonce service (capital/initial-context :simple :echo-service [cache-intc echo-intc]))

(defn send-message [msg]
  (println (str "sending " msg " ..."))
  (let [response (<!! (capital/<send! {:text msg :cache? true} service))]
    (if (contains? response :error)
      (do
        (println "error:")
        (:error response))
      (:text response))))

(defn current-cache []
  (deref (:cache service)))
