# clj-dynamodb-session

A [DynamoDB](http://aws.amazon.com/dynamodb/)-backed
[Ring](https://github.com/ring-clojure) session store.

## Installation

[![Clojars Project](https://clojars.org/gws/clj-dynamodb-session/latest-version.svg)](https://clojars.org/gws/clj-dynamodb-session)

## Quick Start

You will need:

- A DynamoDB table with a `HASH` key but no `RANGE` key.
- For the purposes of the quick start, the key should be named `id` (the
  default), but this can be specified in the configuration, so you can call
  yours anything you want.

Require the library:

```clojure
(ns your.app
  (:require [gws.middleware.session.dynamodb :as dynamodb]))
```

Create the session store (if you are following along, make sure the region and
table name match your own):

```clojure
(def session-store
     (dynamodb/dynamodb-store {:region "us-west-2"
                               :table-name "my-app-session"}))
```

Tell Ring to use your custom session store. If you are using
[ring-defaults](https://github.com/ring-clojure/ring-defaults), you would do
something like this:

```clojure
(def my-defaults
  (-> secure-site-defaults
      (assoc-in [:session :store] session-store)))
```

## Configuration

The configuration map you pass to `dynamodb-store` is merged into the default
configuration map. An explanation of each key is below:

### `:client`

If you have a preconfigured Amazon DynamoDB client (using the Java SDK), you can
pass it in here. If it is set, `:region` and `:endpoint` will be ignored, as the
client is assumed to be configured.

- Required: Yes (one of `:client`, `:endpoint`, or `:region` must be set)
- Default: `nil`

### `:region`

This is the AWS region to use. If this is set, `:endpoint` will be ignored.

- Required: Yes (one of `:client`, `:endpoint`, or `:region` must be set)
- Default: `nil`

### `:endpoint`

This is the endpoint at which the DynamoDB service (or one just like it) will be
located. This is useful for testing with the DynamoDB Local service. This will
only be used if `:client` and `:region` are not set.

- Required: Yes (one of `:client`, `:endpoint`, or `:region` must be set)
- Default: `"http://localhost:8000"`

### `:table-name`

The name of the DynamoDB table.

- Required: Yes
- Default: `"clj-dynamodb-session"`

### `:id-attribute-name`

This is the name of the "primary key" (`HASH`) attribute on the table, and will
contain the session key.

- Required: Yes
- Default: `"id"`

### `:data-attribute-name`

This is the name of the attribute that will contain the Transit-serialized
session data.

- Required: Yes
- Default: `"data"`

### `:custom-attributes`

This will contain a map of attribute name to 1-arity function. The function will
accept the (deserialized) session data as its argument, and return an
[AttributeValue](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/model/AttributeValue.html)
that will be placed into the session item at that attribute when the item is
written to the table. This way, we can natively support any data type supported
by DynamoDB.

This is very useful if you, for example, want to add an application-specific
user ID to the session item (maybe to implement per-user session revocation), or
track the session "last-updated" date for a job to come along later and scan the
table for old session data.

- Required: Yes
- Default: `nil`

To write your own, you might do something like this:

```clojure
(ns my.app
  (:import [com.amazonaws.services.dynamodbv2.model AttributeValue]))

; Assume `data` contains `{:my.app/user-id "abc123"}`
(defn session->user-id
  [data]
  ; By default, this constructor signature will use the `String` type for the
  ; data. You can supply any type you want, using `.with{X}` or `.set{X}` on the
  ; object constructed with the 0-arity constructor. See the docs linked above
  ; for more detail.
  (AttributeValue. ^String (:my.app/user-id data)))
```

Then, to use it, you would place the following entry in your DynamoDB session
store options map:

```clojure
{... YOUR OTHER OPTIONS ...
 :custom-attributes {"user_id" session->user-id}}
```

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
