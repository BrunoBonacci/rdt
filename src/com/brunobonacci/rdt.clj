(ns com.brunobonacci.rdt
  (:require [com.brunobonacci.rdt.internal :as i]))



(defmacro repl-test
  "A top level macro to wrap your test assertions.

  Example:

  ``` clojure
  (repl-test \"testing addition\"
    (reduce + (range 1000)) => 499500)
  ```
  "
  [& forms]
  `(i/eval-test
     ~(i/generate-test-function forms *ns* &form)))



(comment

  (repl-test "sample test"
    :ok
    (def foo 1)
    (def bar 2)

    (println foo)
    (println bar)

    (def foo 2)
    (println (+ foo bar))

    (assoc {} :foo (+ foo bar) :bar 22)
    => {:foo 4 }

    (println "end")
    )



  (repl-test {:labels [:foo]}
    "sample test with labels"

    :ok
    (def foo 1)
    (inc foo)
    => 2

    (println "end")
    )



  (defn f [] [2 {:a 1 :b 2 :c 3} 3 4])

  (repl-test
    (f) => [2 {:a 1 :b 2 }]
    (f) ==> [2 {:a 1 :b 2 :c 3} 3 4]
    )

  )
