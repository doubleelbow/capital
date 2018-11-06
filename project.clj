(defproject com.doubleelbow.capital/capital "0.1.0-SNAPSHOT"
  :description "Helps with external system calls"
  :url "https://github.com/doubleelbow/capital"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/core.async "0.4.474"]
                 [io.pedestal/pedestal.log "0.5.4"]
                 [clj-time "0.14.4"]]
  :plugins [[lein-sub "0.3.0"]]
  :sub ["dev/examples/echo"]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.3.0-alpha1"]]}
             :plugins [[com.doubleelbow/lein-deploy-prepared "0.1.0"]]}
  :repositories [["snapshots" {:url "https://repo.clojars.org"
                               :username :env/clojars_user
                               :password :env/clojars_pass}]
                 ["releases" {:url "https://repo.clojars.org"
                              :username :env/clojars_user
                              :password :env/clojars_pass}]])
