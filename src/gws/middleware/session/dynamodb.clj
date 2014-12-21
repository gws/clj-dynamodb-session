(ns gws.middleware.session.dynamodb
  (:require [amazonica.aws.dynamodbv2 :as dynamodb]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [ring.middleware.session.store :refer [SessionStore]])
  (:import [com.amazonaws.services.dynamodbv2.model ResourceInUseException
                                                    ResourceNotFoundException]
           [java.io ByteArrayInputStream
                    ByteArrayOutputStream]
           [java.nio Buffer]
           [java.util Date
                      UUID]))

(def default-options
  "Default options suitable for testing DynamoDB locally."
  {:access-key "fill-me-in"
   :secret-key "fill-me-in"
   :endpoint "http://localhost:8000"
   :table-name "clj-dynamodb-sessions"
   :session-timeout 600
   :read-capacity-units 5
   :write-capacity-units 5})

(def ^:private non-ddb-keys
  [:table-name
   :session-timeout
   :read-capacity-units
   :write-capacity-units])

(deftype DynamoDBStore [options table-name session-timeout]
  SessionStore

  (read-session [store key]
    (when (not (empty? key))
      (let [result (dynamodb/get-item options
                                      :table-name table-name
                                      :key {:id {:s key}}
                                      :consistent-read true)
            expires-at (get-in result [:item :expires_at])]
        (when (and expires-at (.after (Date. (long expires-at)) (Date.)))
          (when-let [^Buffer data (get-in result [:item :data])]
            (let [reader (transit/reader (ByteArrayInputStream. (.array data))
                                         :json)]
              (transit/read reader)))))))
  (write-session [store key data]
    (let [key (or key (str (UUID/randomUUID)))
          out (ByteArrayOutputStream. 4096)
          writer (transit/writer out :json)]
      (transit/write writer data)
      (let [item {:id key
                  :data (.toByteArray out)
                  :expires_at (+ (System/currentTimeMillis)
                                 (* session-timeout 1000))}]
        (dynamodb/put-item options
                           :table-name table-name
                           :item item)
        key)))
  (delete-session [store key]
    (try
      (dynamodb/delete-item options
                            :table-name table-name
                            :key {:id {:s key}})
      (catch ResourceNotFoundException _ nil))
    nil))

(defn ^:private create-sessions-table
  [options table-name rcu wcu]
  (dynamodb/create-table options
                         :table-name table-name
                         :key-schema [{:attribute-name "id" :key-type "HASH"}]
                         :attribute-definitions [{:attribute-name "id" :attribute-type "S"}]
                         :provisioned-throughput {:read-capacity-units rcu :write-capacity-units wcu}))

(defn dynamodb-store
  ""
  ([] (dynamodb-store {}))
  ([options]
    (let [options (merge default-options options)
          [table-name session-timeout rcu wcu] ((apply juxt non-ddb-keys) options)
          options (apply dissoc options non-ddb-keys)]
      (try
        (create-sessions-table options table-name rcu wcu)
        (catch ResourceInUseException e
          (log/debug "Session table already exists, skipping its creation.")))
      (DynamoDBStore. options table-name session-timeout))))
