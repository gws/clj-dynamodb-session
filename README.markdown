[![Travis CI build status for clj-dynamodb-session](https://travis-ci.org/gws/clj-dynamodb-session.svg?branch=master)]
(https://travis-ci.org/gws/clj-dynamodb-session)

[![Jarkeeper dependency update status for clj-dynamodb-session](https://jarkeeper.com/gws/clj-dynamodb-session/status.svg)]
(https://jarkeeper.com/gws/clj-dynamodb-session)

# clj-dynamodb-session

A [DynamoDB](http://aws.amazon.com/dynamodb/)-backed
[Ring](https://github.com/ring-clojure) session store.

## Installation

[![Clojars Project](https://clojars.org/gws/clj-dynamodb-session/latest-version.svg)](https://clojars.org/gws/clj-dynamodb-session)

## Usage

```clojure
(ns your.app
  (:require [gws.middleware.session.dynamodb :as dynamodb]))

;;; 1. Create the store.

;; This will create a default store using a local endpoint, usually for testing.
(def ddb-store (dynamodb/dynamodb-store))

;; The above is equivalent to the following:
;(def ddb-store
;     (dynamodb/dynamodb-store {:client nil
;                               :endpoint "http://localhost:8000"
;                               :region nil
;                               :table-name "clj-dynamodb-sessions"
;                               :read-capacity-units 5
;                               :write-capacity-units 5}))

;; You can (and almost certainly will) override options such as the region and
;; table name:
;(def ddb-store
;     (dynamodb/dynamodb-store {:region "us-west-2"
;                               :table-name "my-app-sessions"}))

;; NOTE: If you set `region`, it will be used *instead* of `endpoint`.

;; You can even create your own Amazon DynamoDB client and pass it in as the
;; value of the `:client` key.

;;; 2. Pass the store to Ring.

;; If you are using ring-defaults, it looks something like this:

(def my-defaults
  (-> secure-site-defaults
      (assoc-in [:session :store] ddb-store)))

;; Now, you will use `my-defaults` as you would in any other Ring application.
```

## Data model

There are 3 fields created in the DynamoDB table that you specify.

- `id` (string): The session identifier.
- `data` (bytes): The session data, encoded with Transit using msgpack.
- `updated_at` (string): The timestamp of the last time the session was written,
  in ISO-8601 format.

Eliminating old sessions, if desired, is left up to you. There are other
middleware that can be used for session expiration, such as
[ring-session-timeout](https://github.com/ring-clojure/ring-session-timeout),
but that will not clean data out of DynamoDB. You may want to periodically purge
old sessions based on the length of time you keep them using the `updated_at`
field.

## Testing

Currently, running the tests requires a live DynamoDB instance and the default
is to use the endpoint at `http://localhost:8000`. There is a
`docker-compose.yml` included in the repository to make this easier.

1. `docker-compose up` (requires `docker-compose` 1.6.0 or later)
2. `lein test`

## License

Copyright 2014 Gordon Stratton

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
