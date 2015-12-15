(defproject gws/clj-dynamodb-session "0.4.1"
  :description "A DynamoDB-backed Ring session store"
  :url "https://gitlab.com/gws/clj-dynamodb-session"
  :min-lein-version "2.0.0"
  :license {:name "Apache 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[com.amazonaws/aws-java-sdk-core "1.10.40"]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.10.40"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ring/ring-core "1.4.0"]]
  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0-RC3"]]
                   :plugins [[jonase/eastwood "0.2.3"]]}})
