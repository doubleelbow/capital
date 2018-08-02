(ns echo.core
  (:require [io.pedestal.log :as log]
            [clojure.core.async :refer [<!!]]
            [com.doubleelbow.capital.alpha :as capital]
            [com.doubleelbow.capital.interceptor.alpha :as interceptor]
            [clojure.string :as str])
  (:gen-class))

(def ^:private cache-intc
  {::interceptor/name :cache
   ::interceptor/init {:cache (atom {})}
   ::interceptor/aliases [:before :after]
   :before (fn [ctx]
             (let [text (get-in ctx [::capital/request :text])
                   cache (:cache ctx)]
               (if (contains? @cache text)
                 (do
                   (log/debug :msg "using cache" :request text)
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
                  (do
                    (log/debug :msg "storing cache" :request text)
                    (swap! cache assoc text 0))
                  ctx)
                ctx)
              ctx))})

(def ^:private echo-intc
  {::interceptor/name :echo
   ::interceptor/up (fn [ctx]
                      (let [request (::capital/request ctx)
                            text (:text request)]
                        (if (.contains text "error")
                          (do
                            (log/error :msg "found error" :request text)
                            (throw (Exception. "error found in response")))
                          (assoc ctx ::capital/response request))))
   ::interceptor/error (fn [ctx e]
                         (do
                           (assoc ctx ::capital/response {:error (ex-data e)})))})

(defonce service (capital/initial-context :simple :echo-service [cache-intc echo-intc]))

(defn- send-message [msg]
  (log/debug :msg (str "sending " msg " ..."))
  (let [response (<!! (capital/<send! {:text msg :cache? true} service))]
    (log/debug :msg "response" :response response)
    (if (contains? response :error)
      (:error response)
      (:text response))))

(defn- current-cache []
  (log/debug :msg "obtaining current cache")
  (deref (:cache service)))

(defn -main
  [& args]
  (log/info :msg "starting echo app")
  (print "\nEnter the message you'd like to send.\n\tcache - shows current cache\n\texit - exits the app\n\nmessage: ")
  (flush)
  (loop [input (str/trim (read-line))]
    (do
      (case (str/lower-case input)
        "" (do
             (log/warn :msg "empty message")
             (println "empty message not allowed"))
        "exit" (do
                 (log/info :msg "closing echo app")
                 (println "bye")
                 (System/exit 0))
        "cache" (println (deref (:cache service)))
        (println (send-message input)))
      (print "message: ")
      (flush)
      (recur (read-line)))))
