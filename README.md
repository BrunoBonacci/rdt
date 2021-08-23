# RDT - REPL-Driven tests
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/rdt.svg)](https://clojars.org/com.brunobonacci/rdt)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/rdt)](https://cljdoc.org/d/com.brunobonacci/rdt/CURRENT) [![CircleCI](https://circleci.com/gh/BrunoBonacci/rdt/tree/main.svg?style=shield)](https://circleci.com/gh/BrunoBonacci/rdt/tree/main) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/rdt.svg)

RDT is a REPL-Driven Test library for Clojure. It enables you to write
tests that look like REPL sessions. It makes use of pattern matching and
other functionalities to make the tests more readable.

RDT is a library designed to create tests that are like REPL sessions,
low structure, easy to debug when they fail because they are just REPL
sessions. _This library favours tests readability over design
purity._

RDT is piggybacking on top of the great work of **Midje** adding a
couple of features for readability. I don't know whether it will always
depend on Midje, or whether in the future it might just be its own
thing, but for the moment it does internally use it, so the tooling
compatible with Midje will (generally) be compatible with RDT as well.

**WORK IN PROGRESS:** Some behaviours might change.


## Motivation

I strongly believe that tests should be "obvious" to the reader. I've
seen tests that were more complex than the code they were testing. I
believe that libraries such as [Midje](https://github.com/marick/Midje)
make a good attempt to make tests more readable to the developer. I
think Midje is largely underappreciated in our community.

Most of the testing libraries have a strong focus on unit-tests, which
in a functional language like Clojure tend to be pure functions which
are stateless.

So the general pattern is something like:

``` clojure
;; midje tests example
(fact "some statement"
  (function arg1 arg2 arg3) => expected-result)
```

However, when tests are stateful, such as most of the integration tests,
the test starts to be more and more complex.

The following example is a test which verifies the correct functionality
of a queueing system.

``` clojure
;; midje tests example
(fact "items sent to a queue can be retrieved from the same"

  (let [cfg    (start-test-cluster)
        q-name (str (java.util.UUID/randomUUID))
        queue  (create-queue cfg q-name)]

    (pop-items cfg queue) => empty?

    (let [item {:some :message :random (rand)}
          msg  (send cfg queue item)]

      msg => (contains {:message-id string?})

      (let [q-item (first (pop-items cfg queue))]

        q-item => (contains {:message-id (:message-id msg)
                             :item item})

        (delete-item cfg queue q-item) => (contains {:status :deleted})))))
```

This test is a simplified version of a real-world test, however it is
already explicative of the problem faced.

Assume that that this test is failing, in order to debug you will need
to start the REPL and evaluate each expression in order. However,
because of the nested `let` expressions, you will need to **unnest** it
first, which is basically rewriting the whole test in the repl.

Let's see the same test written in RDT:

``` clojure
;; with RDT
(repl-test "items sent to a queue can be retrieved from the same"

  (def cfg    (start-test-cluster))
  (def q-name (str (java.util.UUID/randomUUID)))
  (def queue  (create-queue cfg q-name))

  (pop-items cfg queue) => empty?

  (def item {:some :message :random (rand)})
  (def msg  (send cfg queue item))

  msg => {:message-id string?}

  (def q-item (first (pop-items cfg queue)))

  q-item => {:message-id (:message-id msg)
             :item item}

  (delete-item cfg queue q-item) => {:status :deleted})
```

I don't think there is any doubt about which one of the two tests it is
easier to read and understand what is happening. Moreover, if the test
fails, it is extremely easy to start the REPL and evaluate each
expression to debug and understand where is the problem.

Behind the scenes, RDT converts the test from the second form to the
first form, so the two tests are equivalent, all the `def` are valid
within the scope of `repl-test` only.

## Usage

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/rdt "0.1.0"]

;; deps.edn format
{:deps { com.brunobonacci/rdt {:mvn/version "0.1.0"}}}
```

Current version:
[![](https://img.shields.io/clojars/v/com.brunobonacci/rdt.svg)](https://clojars.org/com.brunobonacci/rdt)

Then require the namespace:

``` clojure
(ns foo.bar
  (:require [com.brunobonacci.rdt :refer [repl-test]]))
```

Check the [online
documentation](https://cljdoc.org/d/com.brunobonacci/rdt/CURRENT)

### Writing tests


The general form is:

``` clojure
(repl-test "description of what you want to test (optional)"

    (function1 arg1 arg2 arg3) => expected-value1

    (function2 arg1 arg2 arg3) => expected-value2

    (functionN arg1 arg2 arg3) => expected-valueN
 )
```

The left side of the arrow `=>` is the function you want to test, the
right side of the arrow is the value or pattern you expect.

### Fuzzy matching arrow `=>`


The single arrow (`=>`) performs a fuzzy match of the left-hand-side of
the arrow with the right-hand-side of the arrow. It works differently
than Midje.

The fuzzy match work as follow:

#### Basic values are matched with equality (`=`)

``` clojure
(repl-test
  (reduce + (range 1000)) => 499500    ;; numbers
  (str "foo" "-" "bar")   => "foo-bar" ;; strings
  (number? 23)            => true      ;; boolean
  (keyword "foo")         => :foo      ;; keywords
  (quote foo)             => 'foo      ;; symbols
  (first [])              => nil       ;; nil
  )
```

#### Lists and Vectors are matched on the given prefix

``` clojure
(repl-test
  (vector)       => []    ;; true
  (vector 1)     => [1]   ;; true
  (vector 2)     => [1]   ;; false, test fails
  (vector 1 2)   => [1 3] ;; false, test fails, [1 3] is not a prefix of [1 2]
  (vector 1 2 3) => [1 2] ;; true, [1 2] is a prefix of [1 2 3]

  (list)         => '()   ;; true
  (list 1)       => '(1)  ;; true
  (list 2)       => '(1)  ;; false, test fails
  (list 1 2)     => '(1 3);; false, test fails, (1 3) is not a prefix of (1 2)
  (list 1 2 3)   => '(1 2);; true, (1 2) is a prefix of (1 2 3)
)
```

#### Maps are matched only on the given keys

Maps are matched only on the subset of keys which are present on the
right-hand-side, the actual map can have more keys.

``` clojure
(repl-test
  (conj  {} :s 2)            => {:s 2}      ;; true
  (merge {:s 2} {:b 3}       => {:s 2}      ;; true, the map contain the key :s with the value 2
  (conj  {:s 2 :b 3} [:x 4]) => {:s 2 :x 4} ;; true, all required keys and values are there
  (conj  {:s 2 :b 3} [:Z 4]) => {:s 2 :x 4} ;; false, key :x is missing
)
```

#### Sets are matched based on the given subset of values

``` clojure
(repl-test
  (set 1 2 3)  => #{1 3 2} ;; true, the sets are the same
  (set 1 2 3)  => #{1 3}   ;; true, #{1 3} is a subset of #{1 2 3}
  (set 1 2 3)  => #{1 4}   ;; false, #{1 4} is NOT a subset of #{1 2 3}
)
```

#### Match against functions

In the right-hand-side you can use functions in place of values, in that
case the function will be applied to the value and check whether the
result is true or false.

``` clojure
(repl-test
  (+ 1 2 3)                   => number?  ;; true
  {:num 1 :str "two"}         => map?     ;; true
  {:num 1 :str "two" :k :z}   => {:num odd? :str string?} ;; all true,
)
```

#### Regex to match strings

In the right-hand-side you can use regular expression patterns in place
of values, in that case the pattern will be matched against the value
and check whether the result is true or false.

``` clojure
(repl-test
  (str "foo" 123) => #"foo.*"       ;; true
  (str "foo" 123) => #"foo"         ;; false, not a full match
  (str "foo" 123) => #"(?i)^FO+\d+" ;; true
  )
```

### Why the fuzzy matcher?


Integration tests often return big nested maps with many keys, many of
which values represent system state. On the other hand, your tests are
often only focused on verifying that only specific keys have a
particular value. The fuzzy matching is very useful in that case, as it
only will match against the pattern you provided.

For example assume you have a function which returns a map with many
nested keys and you are interested on testing only a few specific keys
on the nested map, then the fuzzy match helps very much with it and
allows for a very readable test.

For example assume you have a function which returns the current product
availability if your depots:

``` clojure
(product-availability "SKU123ABZ")
;;=> {:product-id  "SKU123ABZ"
;;    :description "Next generation 3D printer"
;;    :availability
;;    {:free-stock {"LON1" {:location-id "LON1"
;;                          :description "London depot"
;;                          :quantity    13}
;;                  "BER1" {:location-id "BER1"
;;                          :description "Berlin depot"
;;                          :quantity    3}}
;;     :reserved   {"LON1" 0
;;                  "BER1" 0}}}
```

Let's assume you want to test a function which reverses some stock, if
available, then you can write a test as follow:

``` clojure
(repl-test "Stock reservation"

  (reserve-stock "SKU123ABZ" "LON1" 10)
  => {:product-id  "SKU123ABZ"
      :availability
      {:free-stock {"LON1" {:quantity 3}}
       :reserved   {"LON1" 10}}}
  )
```

As you see in the above test you can specify only the subset of the map
which is relevant to your test and ignore the rest. So much better for
readability.

### What if I need more precise matching?


If you need an exact matching or something in between the exact matching
and the fuzzy matching then you can use the **double-equal arrow** `==>`
which behaves like Midje `=>` arrow.

``` clojure
;; same behaviour as Midje arrow =>
(repl-test "Stock reservation"

  (reserve-stock "SKU123ABZ" "LON1" 10)
  ==> (contains
        {:product-id  "SKU123ABZ"
         :availability
         (contains
           {:free-stock (contains {"LON1" (contains {:quantity 3})})
            :reserved   (contains {"LON1" 10})})})
  )
```

## License

Copyright © 2021 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
