(ns gws.middleware.session.dynamodb-test
  (:require [clojure.test :refer :all]
            [gws.middleware.session.dynamodb :as dynamodb]
            [ring.middleware.session.store :as store])
  (:import [gws.middleware.session.dynamodb DynamoDBStore]
           [java.util UUID]))

(def ^:private test-data
  {:map {:a 1 :b 2 :c 3 :d {:e 4 :f {:g 5 :h 6 :i 7}}}
   :map-empty {}
   :vector [1 2 3 4 5 [6 7 8 [9 10]]]
   :vector-empty []})

(defn- dynamodb-store
  ([]
    (dynamodb/dynamodb-store {:table-name "gws_middleware_session_dynamodb_test"}))
  ([table-name]
    (dynamodb/dynamodb-store {:table-name table-name})))

(deftest create-store
  (let [table-name "gws_middleware_session_dynamodb_test_idempotence"]
    (testing "Create fresh store"
      (is (instance? DynamoDBStore (dynamodb-store table-name))))
    (testing "Creating a store is idempotent"
      (is (instance? DynamoDBStore (dynamodb-store table-name))))))

(deftest read-session
  (let [store (dynamodb-store)]
    (testing "Reading nonexistent key results in nil"
      (is (nil? (store/read-session store "42069"))))
    (testing "Read written data"
      (is (= "42069" (store/write-session store "42069" test-data)))
      (is (= test-data (store/read-session store "42069"))))))

(deftest write-session
  (let [store (dynamodb-store)]
    (testing "Writing a session with a nil key results in new session ID"
      (let [id (store/write-session store nil test-data)]
        (is (string? id))
        (is (= test-data (store/read-session store id)))
        (is (nil? (store/delete-session store id)))))))

(deftest delete-session
  (let [store (dynamodb-store)]
    (testing "Delete written session"
      (is (= "42069" (store/write-session store "42069" test-data)))
      (is (nil? (store/delete-session store "42069")))
      (is (nil? (store/read-session store "42069"))))
    (testing "Deleting nonexistent key results in nil"
      (is (nil? (store/delete-session store "something-i-never-added"))))))
