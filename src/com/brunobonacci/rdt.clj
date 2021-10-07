(ns com.brunobonacci.rdt
  (:require [where.core :refer [where]]
            [midje.sweet :as m]
            [midje.checking.core :as checking]
            [clojure.zip :as zip]
            [clojure.pprint]
            [potemkin]))



(comment
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
      (nested-lets forms))))



(defmacro def->let-flat
  {:no-doc true}
  [& body]
  (let [def?  (where [:and [list? :is? true] [first :is? 'def]])
        defn? (where [:and [list? :is? true] [first :is? 'defn]])
        dummy (gensym "_val_")
        bindings (mapcat
                   (fn [sexpr]
                     (cond
                       (def?  sexpr) (rest sexpr)
                       (defn? sexpr) (let [[sym & fundef] (rest sexpr)] [sym (cons `fn fundef)])
                       :else [dummy sexpr]))
                   body)
        ]
    `(let ~(vec bindings)
       ~dummy)))



(def primitive-arrays #{(Class/forName "[B") ;; bytes
                        (Class/forName "[S") ;; shorts
                        (Class/forName "[I") ;; ints
                        (Class/forName "[J") ;; longs
                        (Class/forName "[F") ;; floats
                        (Class/forName "[D") ;; doubles
                        (Class/forName "[C") ;; chars
                        (Class/forName "[Z") ;; booleans
                        (Class/forName "[Ljava.lang.Object;") ;; objects
                        })

(defn primitive-array?
  "Returns true if x is a primitive array."
  [x]
  (-> x type primitive-arrays boolean))



(defn- atomic-value?
  "Returns true if `value` is atomic, false otherwise."
  [value]
  (some #(% value) [nil? boolean? number? keyword? string? symbol? char?]))



(defn- zip-lists
  ([l1 l2]
   (zip-lists nil l1 l2))
  ([empty-value l1 l2]
   (let [l1 (concat l1 (repeat :rdt/<missing-value>))
         l2 (concat l2 (repeat :rdt/<missing-value>))]
     (->> (map vector l1 l2)
       (take-while (partial some #(not= :rdt/<missing-value> %)))
       (map (fn [e] (mapv #(if (= :rdt/<missing-value> %) empty-value %) e)))))))



(defn- fetch-keys
  [m keys]
  (into {}
    (for [k keys]
      [k (get m k :rdt/<missing-value>)])))



(defn- match-error
  [rpattern rvalue ppattern pvalue pattern value]
  (throw
    (ex-info
      (format "Unable to match pattern <%s> to value <%s>.\n\n\tExpected:\n\t  %s\n\n\tFound:\n\t  %s\n\n\n"
        (pr-str pattern) (pr-str value) (pr-str ppattern) (pr-str pvalue))
      {:error-type     ::match-failed
       :pattern        pattern
       :value          value
       :parent-pattern ppattern
       :parent-value   pvalue
       :root-pattern   rpattern
       :root-value     rvalue})))



(defn- regex?
  [pattern]
  (= java.util.regex.Pattern (type pattern)))



;; TODO: add regex->regex, array->array, regex->string, fn->val
(defn- subset-matcher
  ([pattern value]
   (subset-matcher pattern value pattern value pattern value))
  ([rpattern rvalue ppattern pvalue pattern value]
   (cond
     (and (atomic-value? pattern) (atomic-value? value))
     (or (= pattern value)
       (and (number? pattern) (number? value) (== pattern value))
       (match-error rpattern rvalue ppattern pvalue pattern value))

     (and (sequential? pattern) (primitive-array? value))
     (subset-matcher rpattern rvalue ppattern pvalue pattern (into [] value))

     (and (sequential? pattern) (sequential? value))
     (->> (zip-lists :rdt/<missing-value> pattern value)
       (filter (where first not= :rdt/<missing-value>))
       (map (partial apply subset-matcher rpattern rvalue pattern value))
       (every? true?))

     (and (set? pattern) (set? value))
     (or (clojure.set/subset? pattern value)
       (match-error rpattern rvalue ppattern pvalue pattern value))

     (and (map? pattern) (map? value))
     (->> pattern
       keys
       (fetch-keys value)
       (map (partial subset-matcher rpattern rvalue pattern value) pattern)
       (every? true?))

     (and (fn? pattern) (not (fn? value)))
     (or (pattern value)
       (match-error rpattern rvalue ppattern pvalue pattern value))

     (and (regex? pattern) (string? value))
     (or (boolean (re-matches pattern value))
       (match-error rpattern rvalue ppattern pvalue pattern value))

     :else
     (match-error rpattern rvalue ppattern pvalue pattern value))))



(defn fuzzy-checker
  "A checker for the right-hand-side of the arrow which only checks the keys and items
   in the pattern and accepts additional keys without failing.
   See README.md for more info."
  [expected]
  (fn [actual]
    (try
      (subset-matcher expected actual)
      (catch Exception x
        (if (= ::match-failed (:error-type (ex-data x)))
          (checking/as-data-laden-falsehood
            {:notes [(ex-message x) #_(with-out-str (clojure.pprint/pprint (ex-data x)))]})
          (throw x))))))



;;
;; clojure zippers are awesome!
;;
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
                 (-> z (zip/next) (zip/edit (fn [v] (list `fuzzy-checker v))) (zip/next))

                 :else (zip/next z)))))))



(defmacro repl-test
  "A top level macro to wrap your test assertions.

  Example:

  ``` clojure
  (repl-test \"testing addition\"
    (reduce + (range 1000)) => 499500)
  ```
  "
  [& [doc & facts :as body]]
  (let [test-name (if (string? doc) doc "REPL tests")
        tests (if (string? doc) facts body)
        tests (apply-fuzzy-checker tests)]
    `(m/facts ~test-name
       (def->let-flat
         ~@tests))))



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


  (defn f [] [2 {:a 1 :b 2 :c 3} 3 4])

  (repl-test
    (f) => [2 {:a 1 :b 2 }]
    (f) ==> [2 {:a 1 :b 2 :c 3} 3 4]
    )

  )
