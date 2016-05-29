; Copyright 2014 Gordon Stratton
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns gws.middleware.session.dynamodb
  (:require [cognitect.transit :as transit]
            [ring.middleware.session.store :refer [SessionStore]])
  (:import [com.amazonaws AmazonServiceException]
           [com.amazonaws.regions Region
                                  Regions]
           [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
           [com.amazonaws.services.dynamodbv2.model AttributeValue
                                                    AttributeDefinition
                                                    CreateTableRequest
                                                    GetItemRequest
                                                    KeySchemaElement
                                                    ProvisionedThroughput]
           [com.amazonaws.services.dynamodbv2.util TableUtils]
           [java.io ByteArrayInputStream
                    ByteArrayOutputStream]
           [java.nio ByteBuffer]
           [java.text SimpleDateFormat]
           [java.util Date
                      TimeZone
                      UUID]))

(defrecord Options [client endpoint region table-name read-capacity-units
                    write-capacity-units])

(def default-options
  "Default options suitable for testing DynamoDB locally."
  {:client nil
   :endpoint "http://localhost:8000"
   :region nil
   :table-name "clj-dynamodb-sessions"
   :read-capacity-units 5
   :write-capacity-units 5})

(def ^java.text.DateFormat date-format
  (doto (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss'Z'")
        (.setTimeZone (TimeZone/getTimeZone "UTC"))))

(defn- decode
  "Decodes a java.nio.ByteBuffer into a Clojure data structure."
  [^ByteBuffer data]
  (-> (ByteArrayInputStream. (.array data))
      (transit/reader :msgpack)
      (transit/read)))

(defn- encode
  "Encodes a Clojure data structure into a java.nio.ByteBuffer."
  ^java.nio.ByteBuffer
  [data]
  (let [baos (ByteArrayOutputStream. 4096)]
    (transit/write (transit/writer baos :msgpack) data)
    (ByteBuffer/wrap (.toByteArray baos))))

(defn- region-from-string
  "Returns a Region object given a string representation of an AWS region."
  ^com.amazonaws.regions.Region
  [region]
  (Region/getRegion (Regions/fromName region)))

(defn- create-session-table!
  "Create the DynamoDB table which will hold session data."
  [^AmazonDynamoDBClient client options]
  (let [request (CreateTableRequest.
                  [(AttributeDefinition. "id" "S")]
                  (:table-name options)
                  [(KeySchemaElement. "id" "HASH")]
                  (ProvisionedThroughput. (:read-capacity-units options)
                                          (:write-capacity-units options)))]
    (let [_ (TableUtils/createTableIfNotExists client request)]
      (TableUtils/waitUntilActive client (:table-name options)))))

(deftype DynamoDBStore [^AmazonDynamoDBClient client options]
  SessionStore

  (read-session [store key]
    (when key
      (let [item {"id" (.withS (AttributeValue.) key)}
            request (-> (GetItemRequest. (:table-name options) item true)
                        (.withExpressionAttributeNames {"#d" "data"})
                        (.withProjectionExpression "#d"))
            result (.getItem client request)]
        (when-let [item (.getItem result)]
          (when-let [data (.getB ^AttributeValue (get item "data"))]
            (decode data))))))
  (write-session [store key data]
    (let [key (or key (str (UUID/randomUUID)))
          now-str (.format date-format (Date.))
          item {"id" (.withS (AttributeValue.) key)
                "data" (.withB (AttributeValue.) (encode data))
                "updated_at" (.withS (AttributeValue.) now-str)}]
      (.putItem client (:table-name options) item)
      key))
  (delete-session [store key]
    (when key
      (let [item {"id" (.withS (AttributeValue.) key)}]
        (.deleteItem client (:table-name options) item)
        nil))))

(defn dynamodb-client
  "Creates an AmazonDynamoDBClient from an options map."
  ^com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
  [options]
  (let [options (map->Options (merge default-options options))
        client (or ^AmazonDynamoDBClient (:client options)
                   (AmazonDynamoDBClient.))]
    (if (:region options)
      (.withRegion client (region-from-string (:region options)))
      (.withEndpoint client (:endpoint options)))))

(defn dynamodb-store
  "Create a new DynamoDB session store implementing the SessionStore protocol.

  See default-options for example options."
  ([]
   (dynamodb-store {}))
  ([options]
   (let [client (dynamodb-client options)]
     (create-session-table! client options)
     (DynamoDBStore. client options))))
