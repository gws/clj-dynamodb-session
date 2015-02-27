(ns gws.middleware.session.dynamodb-test
  (:require [clojure.test :refer :all]
            [gws.middleware.session.dynamodb :as ddb]
            [ring.middleware.session.store :as store])
  (:import [gws.middleware.session.dynamodb DynamoDBStore]
           [java.util UUID]))

(def ^:private test-data
  {:map {:a 1 :b 2 :c 3 :d {:e 4 :f {:g 5 :h 6 :i 7}}}
   :map-empty {}
   :vector [1 2 3 4 5 [6 7 8 [9 10]]]
   :vector-empty []})

(def ^:private table
  (str (UUID/randomUUID)))

(defn- store
  [table]
  (ddb/dynamodb-store {} {:table-name table}))

(deftest create
  (testing "Create fresh store"
    (is (instance? DynamoDBStore (store table))))
  (testing "dynamodb-store is idempotent"
    (is (instance? DynamoDBStore (store table)))))

(deftest read-writes
  (let [store (store table)]
    (is (= "42069" (store/write-session store "42069" test-data)))
    (is (= test-data (store/read-session store "42069")))))

(deftest delete-key
  (let [store (store table)]
    (is (= "42069" (store/write-session store "42069" test-data)))
    (is (= nil (store/delete-session store "42069")))))

(deftest delete-nonexistent-key
  (let [store (store table)]
    (is (= nil (store/delete-session store "something-i-never-added")))))

(deftest write-with-nil-key
  (let [store (store table)
        id (store/write-session store nil test-data)]
    (is (string? id))
    (is (= test-data (store/read-session store id)))
    (is (= nil (store/delete-session store id)))))
