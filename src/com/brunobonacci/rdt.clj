(ns com.brunobonacci.rdt
  (:require [where.core :refer [where]]
            [midje.sweet :as m]))



(defmacro def->let-nested
  [& body]
  (let [ ;; body seq of expressions
        ;; forms -> a seq of seq of expressions
        forms (partition-by (where [:and [list? :is? true] [first :is? 'def]]) body)
        ;; forms -> a seq of defs followed by a seq of expressions
        forms (if (and (list? (ffirst forms)) (= 'def (first (ffirst forms)))) forms (cons '() forms))
        ;; forms -> a seq of tuples of defs followed by expressions
        forms (partition-all 2 forms)

        nested-lets (fn nested-lets [[fforms & rforms]]
                      (if (nil? (first fforms))
                        nil
                        (let [defs (first fforms)
                              exprs (second fforms)
                              bindings (->> defs (mapcat rest) (vec))]
                          `(let ~bindings (do ~@exprs ~(nested-lets rforms))))))]
    ;; so
    ;; forms -> a seq of tuples of defs followed by expressions
    ;; (first forms) -> tuple of defs followed by expressions -> [defs exprs]
    ;; defs -> seq of def expressions
    ;; exprs -> seq of expressions
    (nested-lets forms)))



(defmacro def->let-flat
  [& body]
  (let [def? (where [:and [list? :is? true] [first :is? 'def]])
        dummy (gensym "_val_")
        bindings (mapcat (fn [sexpr]
                           (if (def? sexpr)
                             (rest sexpr)
                             [dummy sexpr]
                             ))body)
        ]
    `(let ~(vec bindings)
       ~dummy)))



(defmacro repl-test
  [& body]
  `(m/facts "REPL tests"
     (def->let-flat
       ~@body)))



(comment

  (repl-test

    :ok
    (def foo 1)
    (def bar 2)

    (println foo)
    (println bar)

    (def foo 2)
    (println (+ foo bar))

    (assoc {} :foo (+ foo bar))
    => {:foo 4}

    (println "end")
    )

  )
