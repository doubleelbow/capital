(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer (javadoc)]
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [com.doubleelbow.capital.alpha :as capital]
   [clojure.core.async :refer [<!!]]
   [examples.printing :as printing]))

(comment

  (<!! (capital/<send! {} (printing/sync-service 3)))
  (<!! (capital/<send! {} (printing/async-service 3))))
