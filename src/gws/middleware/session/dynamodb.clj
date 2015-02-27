(ns gws.middleware.session.dynamodb
  (:require [amazonica.aws.dynamodbv2 :as dynamodb]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [ring.middleware.session.store :refer [SessionStore]])
  (:import [com.amazonaws AmazonServiceException]
           [com.amazonaws.services.dynamodbv2.model ResourceInUseException
                                                    ResourceNotFoundException]
           [java.io ByteArrayInputStream
                    ByteArrayOutputStream]
           [java.nio Buffer]
           [java.util Date
                      UUID]))

(def default-credentials
  "Default credentials suitable for testing DynamoDB locally."
  {:access-key "fill-me-in"
   :secret-key "fill-me-in"
   :endpoint "http://localhost:8000"})

(def default-options
  "Default options suitable for testing DynamoDB locally."
  {:table-name "clj-dynamodb-sessions"
   :session-timeout 600
   :read-capacity-units 5
   :write-capacity-units 5})

(defn- aws-ex->map
  "Pull important data out of an ASE into a map."
  [^AmazonServiceException e]
  {:aws-request-id (.getRequestId e)
   :aws-service-name (.getServiceName e)
   :aws-error-code (.getErrorCode e)
   :aws-error-message (.getErrorMessage e)})

(defn- decode
  "Decodes bytes from DynamoDB into a Clojure data structure."
  [^Buffer data]
  (let [r (transit/reader (ByteArrayInputStream. (.array data)) :json)]
    (transit/read r)))

(defn- encode
  "Encodes a Clojure data structure into a byte array."
  ^bytes
  [data]
  (let [baos (ByteArrayOutputStream. 4096)
        w (transit/writer baos :json)]
    (transit/write w data)
    (.toByteArray baos)))

(defn- create-session-table!
  "Create the DynamoDB table which will hold session data."
  [creds table rcu wcu]
  (dynamodb/create-table
    creds
    :table-name table
    :key-schema [{:attribute-name "id" :key-type "HASH"}]
    :attribute-definitions [{:attribute-name "id" :attribute-type "S"}]
    :provisioned-throughput {:read-capacity-units rcu
                             :write-capacity-units wcu}))

(deftype DynamoDBStore [credentials table-name session-timeout]
  SessionStore

  (read-session [store key]
    (when (seq key)
      (let [result (dynamodb/get-item credentials
                                      :table-name table-name
                                      :key {:id {:s key}}
                                      :consistent-read true)
            expires-at (get-in result [:item :expires_at])]
        (when (and expires-at (.after (Date. (long expires-at)) (Date.)))
          (when-let [data (get-in result [:item :data])]
            (decode data))))))
  (write-session [store key data]
    (let [key (or key (str (UUID/randomUUID)))]
      (let [item {:id key
                  :data (encode data)
                  :expires_at (+ (System/currentTimeMillis)
                                 (* session-timeout 1000))}]
        (dynamodb/put-item credentials
                           :table-name table-name
                           :item item)
        key)))
  (delete-session [store key]
    (try
      (dynamodb/delete-item credentials
                            :table-name table-name
                            :key {:id {:s key}})
      (catch ResourceNotFoundException _ nil))
    nil))

(defn dynamodb-store
  "Create a new DynamoDB session store implementing the SessionStore protocol.

  See default-credentials and default-options for example options."
  ([]
   (dynamodb-store {} {}))
  ([credentials]
   (dynamodb-store credentials {}))
  ([credentials options]
   (let [creds (merge default-credentials credentials)
         opts (merge default-options options)
         table (:table-name opts)
         rcu (:read-capacity-units opts)
         wcu (:write-capacity-units opts)
         timeout (:session-timeout opts)]
     (try
       (create-session-table! creds table rcu wcu)
       (catch ResourceInUseException e
         (log/debug (json/generate-string (aws-ex->map e)))))
     (DynamoDBStore. creds table timeout))))
