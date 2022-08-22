(ns com.brunobonacci.rdt-test
  (:require [com.brunobonacci.rdt.internal :as i]
            [com.brunobonacci.rdt :refer :all]))


(repl-test "testing repl-test macro: simplest form"

  ((binding [i/*runner* {:type :rdt/test-runner}]
     (repl-test
       (+ 1 1) => 2))
   :test-info)
  =>
  {:id "534fa59d342bb563d3fa4067d4937092c01bcc6374033705d62e6b162255934d",
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
