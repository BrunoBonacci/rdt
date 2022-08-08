(ns com.brunobonacci.rdt
  (:require [com.brunobonacci.rdt.checkers :as chk]
            [midje.sweet :as m]
            [clojure.zip :as zip]
            [clojure.pprint]
            [potemkin]
            [com.brunobonacci.rdt.internal :as i]))



(defn- apply-fuzzy-checker
  [forms]
  (loop [z (zip/seq-zip forms)]
    (if (identical? z (zip/next z))
      (zip/root z)
      (if (zip/branch? z)
        (recur (zip/next z))
        (recur (cond
                 (= (zip/node z) '==>)
                 (-> z (zip/edit (fn [v] (if (= v '==>) '=> v))) (zip/next))

                 (= (zip/node z) '=>)
                 (-> z (zip/next) (zip/edit (fn [v] (list `chk/fuzzy-checker v))) (zip/next))

                 :else (zip/next z)))))))



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
        tests     (apply-fuzzy-checker tests)]
    `(m/facts ~test-name ~@(:labels cfg)
       (i/fact->checks ~tests ~(mapcat identity finals)))))



(potemkin/import-vars
  [midje.sweet

   throws
   just
   contains])



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
