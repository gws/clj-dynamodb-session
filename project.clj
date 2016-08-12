(defproject gws/clj-dynamodb-session "0.5.2"
  :description "A DynamoDB-backed Ring session store"
  :url "https://gitlab.com/gws/clj-dynamodb-session"
  :min-lein-version "2.0.0"
  :license {:name "Apache 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[com.amazonaws/aws-java-sdk-core "1.11.26"]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.11.26"]
                 [com.cognitect/transit-clj "0.8.288"]
                 [ring/ring-core "1.5.0"]]
  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:plugins [[jonase/eastwood "0.2.3"]]}
             :provided {:dependencies [[org.clojure/clojure "1.8.0"]]}})
