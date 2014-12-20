(defproject gws/clj-dynamodb-session "0.1.0"
  :description "A DynamoDB-backed Ring session store"
  :url "https://gitlab.com/gws/clj-dynamodb-session"
  :min-lein-version "2.0.0"
  :license {:name "Apache 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :profiles {:dev {:global-vars {*warn-on-reflection* true}}}
  :dependencies [[com.taoensso/nippy "2.7.1"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [amazonica "0.3.6"]
                 [ring/ring-core "1.3.2"]])