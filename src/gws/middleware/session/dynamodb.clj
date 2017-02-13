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
  (:import [com.amazonaws.regions RegionUtils]
           [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
           [com.amazonaws.services.dynamodbv2.model AttributeValue
                                                    GetItemRequest]
           [java.io ByteArrayInputStream
                    ByteArrayOutputStream]
           [java.nio ByteBuffer]
           [java.util UUID]))

(def default-options
  "Default options for the DynamoDB store."
  {:client nil
   :region nil
   :endpoint "http://localhost:8000"
   :table-name "clj-dynamodb-session"
   :id-attribute-name "id"
   :data-attribute-name "data"
   :custom-attributes {}})

(defn decode
  "Decodes a java.nio.ByteBuffer into a Clojure data structure."
  [^ByteBuffer data]
  (-> (ByteArrayInputStream. (.array data))
      (transit/reader :msgpack)
      (transit/read)))

(defn encode
  "Encodes a Clojure data structure into a java.nio.ByteBuffer."
  ^java.nio.ByteBuffer
  [data]
  (let [baos (ByteArrayOutputStream. 4096)]
    (transit/write (transit/writer baos :msgpack) data)
    (ByteBuffer/wrap (.toByteArray baos))))

(deftype DynamoDBStore [^AmazonDynamoDBClient client options]
  SessionStore

  (read-session [store key]
    (when key
      (let [{id-name :id-attribute-name
             data-name :data-attribute-name
             table-name :table-name}
            options

            pk {id-name (AttributeValue. (str key))}
            request (-> (GetItemRequest. table-name pk true)
                        (.withProjectionExpression "#d")
                        (.withExpressionAttributeNames {"#d" data-name}))]
        (when-let [item (.getItem (.getItem client request))]
          (-> ^AttributeValue (get item data-name)
              (.getB)
              (decode))))))
  (write-session [store key data]
    (let [{id-name :id-attribute-name
           data-name :data-attribute-name
           table-name :table-name
           custom-attrs :custom-attributes}
          options

          key (str (or key (UUID/randomUUID)))
          item (into {id-name (AttributeValue. key)
                      data-name (.withB (AttributeValue.) (encode data))}
                     (map (fn [[k v]] [k (v data)]) custom-attrs))]
      (.putItem client table-name item)
      key))
  (delete-session [store key]
    (when key
      (let [{id-name :id-attribute-name
             table-name :table-name}
            options

            item {id-name (AttributeValue. (str key))}]
        (.deleteItem client table-name item)
        nil))))

(defn dynamodb-client
  "Creates an AmazonDynamoDBClient from the session store options map."
  [options]
  {:post [(instance? AmazonDynamoDBClient %)]}
  (if-let [client (:client options)]
    client
    (let [client (AmazonDynamoDBClient.)]
      (if (:region options)
        (.withRegion client (RegionUtils/getRegion (:region options)))
        (.withEndpoint client (:endpoint options))))))

(defn dynamodb-store
  "Create a new DynamoDB session store implementing the SessionStore protocol.

  See `default-options` for example options."
  ([]
   (dynamodb-store {}))
  ([options]
   (let [options (merge default-options options)
         client (dynamodb-client options)]
     (DynamoDBStore. client options))))
