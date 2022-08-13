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
  (let [cfg       (if (map?    (first forms)) (first forms) {})
        forms     (if (map?    (first forms)) (rest forms) forms)
        test-name (if (string? (first forms)) (first forms) "REPL tests")
        forms     (if (string? (first forms)) (rest forms) forms)
        [tests _ & finals] (partition-by #{:rdt/finalize :rdt/finalise} forms)
        {:keys [line column]} (meta &form)
        site      (str *ns* "[l:" line ", c:" column "]")
        id        (i/test-id &form)
        cfg       (assoc cfg :id id :ns (str *ns*) :form (list `quote &form)
                    :name test-name
                    :location site)]
    `(i/eval-test
       (let [~'test-id ~id ~'test-info ~cfg]
         (fn
           ([cmd#]
            (case cmd#
              :test-id   ~'test-id
              :test-info ~'test-info))
           ([]
            (i/fact->checks ~tests ~(mapcat identity finals))))))))



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
