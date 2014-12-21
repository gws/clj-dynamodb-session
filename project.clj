(defproject gws/clj-dynamodb-session "0.2.0"
  :description "A DynamoDB-backed Ring session store"
  :url "https://gitlab.com/gws/clj-dynamodb-session"
  :min-lein-version "2.0.0"
  :license {:name "Apache 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :profiles {:dev {:global-vars {*warn-on-reflection* true}}}
  :dependencies [[amazonica "0.3.6"]
                 [com.cognitect/transit-clj "0.8.259"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ring/ring-core "1.3.2"]])
