(ns com.brunobonacci.rdt-test
  (:require [com.brunobonacci.rdt.internal :as i]
            [com.brunobonacci.rdt :refer :all]))


(repl-test "testing repl-test macro: simplest form"

  ((binding [i/*runner* {:type :rdt/test-runner}]
     (repl-test
       (+ 1 1) => 2))
   :test-info)
  =>
  {:id "97c0cfc9cda15fee776817c4c649c46daeaa58014e892c913afacbab4ddfc7ff",
   :ns "com.brunobonacci.rdt-test",
   :form '(repl-test (+ 1 1) => 2),
   :name "REPL tests",
   :location string?}

  )




(repl-test "testing repl-test macro: adding test name"

  ((binding [i/*runner* {:type :rdt/test-runner}]
     (repl-test "adding test name"
       (+ 1 1) => 2))
   :test-info)
  =>
  {:name "adding test name"}

  )



(repl-test "testing repl-test macro: adding labels"

  ((binding [i/*runner* {:type :rdt/test-runner}]
     (repl-test {:labels [:foo :bar]} "adding labels"
       (+ 1 1) => 2))
   :test-info)
  =>
  {:labels [:foo :bar]}

  )
