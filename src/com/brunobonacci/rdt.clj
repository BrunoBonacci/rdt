(ns com.brunobonacci.rdt)


(defmacro repl-test
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
