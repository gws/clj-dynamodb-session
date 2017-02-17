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

(ns gws.middleware.session.dynamodb-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [gws.middleware.session.dynamodb :as dynamodb]
            [ring.middleware.session.store :as store])
  (:import [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
           [com.amazonaws.services.dynamodbv2.model AttributeDefinition
                                                    AttributeValue
                                                    CreateTableRequest
                                                    DeleteTableRequest
                                                    KeySchemaElement
                                                    ProvisionedThroughput]
           [com.amazonaws.services.dynamodbv2.util TableUtils]
           [gws.middleware.session.dynamodb DynamoDBStore]
           [java.util UUID]))

(def ^:private any-transit
  "Generator (like `any`) for roundtrippable data via Transit."
  (gen/recursive-gen gen/container-type
                     (gen/one-of [gen/int gen/large-integer
                                  (gen/double* {:infinite? false :NaN? false})
                                  gen/char gen/string gen/ratio gen/boolean
                                  gen/keyword gen/keyword-ns gen/symbol
                                  gen/symbol-ns gen/uuid])))

(def ^:private test-table-name
  (str "gws_middleware_session_dynamodb_test_"
       (UUID/randomUUID)))

(def ^:private store-options
  (merge dynamodb/default-options
         (cond-> {:table-name test-table-name}
           (System/getenv "GMSDT_REGION")
           (assoc :region (System/getenv "GMSDT_REGION"))

           (System/getenv "GMSDT_ENDPOINT")
           (assoc :endpoint (System/getenv "GMSDT_ENDPOINT")))))

(def ^{:private true
       :tag AmazonDynamoDBClient}
     dynamodb-client
  "DynamoDB client for out-of-band test operations."
  (dynamodb/dynamodb-client store-options))

(defn- dynamodb-store
  "Creates a new DynamoDB store with test options"
  ([]
   (dynamodb/dynamodb-store store-options))
  ([overrides]
   (dynamodb/dynamodb-store (merge store-options overrides))))

(defn- create-session-table!
  "Create the DynamoDB table which will hold session data."
  []
  (let [{:keys [table-name]} store-options
        request (CreateTableRequest.
                  [(AttributeDefinition. "id" "S")]
                  table-name
                  [(KeySchemaElement. "id" "HASH")]
                  (ProvisionedThroughput. 2 2))]
    (let [_ (TableUtils/createTableIfNotExists dynamodb-client request)]
      (TableUtils/waitUntilActive dynamodb-client table-name))))

(defn- delete-session-table!
  "Deletes the DynamoDB table whether or not it exists."
  []
  (let [{:keys [table-name]} store-options
        request (DeleteTableRequest. table-name)]
    (TableUtils/deleteTableIfExists dynamodb-client request)))

(defn- wrap-dynamodb-table
  "Cleans up the DynamoDB table after `f` is run."
  [f]
  (create-session-table!)
  (f)
  (delete-session-table!))

(use-fixtures :once wrap-dynamodb-table)

(deftest create-store
  (testing "Create fresh store"
    (is (instance? DynamoDBStore (dynamodb-store))))
  (testing "Creating a store is idempotent"
    (is (instance? DynamoDBStore (dynamodb-store)))))

(deftest read-session
  (let [store (dynamodb-store)]
    (testing "Reading nonexistent key results in nil"
      (is (nil? (store/read-session store (str (UUID/randomUUID))))))))

(defspec read-session-roundtrip
  10
  (let [store (dynamodb-store)]
    (prop/for-all [key gen/uuid
                   data any-transit]
      (.putItem dynamodb-client
                test-table-name
                {(:id-attribute-name store-options)
                 (AttributeValue. (str key))

                 (:data-attribute-name store-options)
                 (.withB (AttributeValue.) (dynamodb/encode data))})
      (= data (store/read-session store (str key))))))

(deftest write-session
  (let [store (dynamodb-store)]
    (testing "Writing a session with a nil key results in new session ID"
      (let [id (store/write-session store nil {:foo "bar"})]
        (is (string? id))))
    (testing "Writing a session with a single custom attribute"
      (let [att-fn #(AttributeValue. ^String (:my.app/user-id %))
            store (dynamodb-store {:custom-attributes {"user_id" att-fn}})
            id (store/write-session store nil {:my.app/user-id "hunter2"})]
        (is (= "hunter2" (-> (.getItem dynamodb-client
                                       test-table-name
                                       {(:id-attribute-name store-options)
                                        (AttributeValue. ^String id)})
                             (.getItem)
                             ^AttributeValue (get "user_id")
                             (.getS))))))))

(defspec write-session-roundtrip
  10
  (let [store (dynamodb-store)]
    (prop/for-all [key gen/uuid
                   data any-transit]
      (store/write-session store (str key) data)
      (= data (-> (.getItem dynamodb-client
                            test-table-name
                            {(:id-attribute-name store-options)
                             (AttributeValue. (str key))})
                  (.getItem)
                  ^AttributeValue (get (:data-attribute-name store-options))
                  (.getB)
                  (dynamodb/decode))))))

(deftest delete-session
  (let [store (dynamodb-store)]
    (testing "Deleting nonexistent key results in nil"
      (is (nil? (store/delete-session store "something-i-never-added"))))))

(defspec always-delete-sessions
  10
  (let [store (dynamodb-store)]
    (prop/for-all [key gen/uuid
                   data any-transit]
      (store/write-session store (str key) data)
      (let [roundtripped? (= data (store/read-session store (str key)))]
        (store/delete-session store (str key))
        (and roundtripped? (nil? (store/read-session store (str key))))))))
