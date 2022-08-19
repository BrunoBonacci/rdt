(ns com.brunobonacci.rdt.internal
  (:require [where.core :refer [where]]
            [com.brunobonacci.rdt.checkers :as chk]
            [clojure.string :as str]))



(def ^:dynamic *runner*
  {:type               :inline

   ;; used in  bacth-runner
   :reporters
   [:rdt/print-summary
    :rdt/print-failures]

   :include-patterns   :all
   :exclude-patterns    nil

   :include-labels     :all
   :exclude-labels      nil

   ;; wrappers
   :test-wrappers
   [:rdt/stats-count-tests   ;; required for reporting
    ;;:rdt/print-test-name
    ;;:rdt/print-test-outcome
    ]

   :expression-wrappers
   [:rdt/stats-count-checks ;; required for reporting
    ]
   :finalizer-wrappers  []


   ;; internal - overridden by compile-wrappers
   :rdt/test-wrapper       (fn [test-info test] test)
   :rdt/expression-wrapper (fn [meta expression] expression)
   :rdt/finalizer-wrapper  (fn [test-info finalizer] finalizer)})




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| U T I L S |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defmacro no-fail
  [& body]
  `(try ~@body (catch Exception x#)))



(defmacro do-with
  [value & forms]
  `(let [~'$it ~value]
     (no-fail ~@forms)
     ~'$it))



(defmacro do-with-exception
  [value ok fail]
  `(let [[~'$it ~'$error] (try [(do ~value) nil] (catch Exception x# [nil x#]))]
     (if ~'$error
       (do (no-fail ~fail) (throw ~'$error))
       (do (no-fail ~ok)   ~'$it))))



(defn sha256
  "hex encoded sha-256 hash"
  [^String data]
  (let [md        (java.security.MessageDigest/getInstance "SHA-256")
        signature (.digest md (.getBytes data "utf-8"))
        size      (* 2 (.getDigestLength md))
        hex-sig   (.toString (BigInteger. 1 signature) 16)
        padding   (str/join (repeat (- size (count hex-sig)) "0"))]
    (str padding hex-sig)))



(defn test-id
  [form]
  (-> form
    (pr-str)
    (str/replace #"__\d+#" "__#")
    (sha256)))



(defmacro thunk
  [& body]
  `(fn [] ~@body))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                ----==| T E R M   R E W R I T I N G |==----                 ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; the strategy is to turn something like
;;
;; ```
;; (repl-test "foo"
;;
;;   (def foo (make-foo 1 2 3))                 ;; def expression
;;   (foo 1) => {:some-val 100}                 ;; checking expression
;;
;;   :rdt/finalize
;;   (shutdown foo)                             ;; finalizer
;;   )                                          ;; to be run whether the test pass or fail
;; ```
;;
;; into something like that
;;
;; ````
;; (let [;; prehamble
;;       root {}
;;       parent-test test
;;       test (inherit parent-test {,,, test-info})
;;       last nil
;;
;;       ;; (def foo (make-foo 1 2 3))
;;       expression-meta {,,,, :test test-info}
;;       expression      (wrap expression-meta (fn [] (make-foo 1 2 3)))
;;       try-expression  (fn [] (try (assoc expression-meta :result (expression))
;;                                  (catch Exception x (assoc expression-meta :error x))))
;;       {foo :result :as last} (if (error? last) last (try-expression))
;;
;;       ;; (foo 1) => {:some-val 100}
;;       expression-meta {,,,, :test test-info}
;;       expression      (wrap expression-meta (fn [] (checker {:some-val 100} (fn [] (foo 1)))))
;;       try-expression  (fn [] (try (assoc expression-meta :result (expression))
;;                                  (catch Exception x (assoc expression-meta :error x))))
;;       last            (if (error? last) last (try-expression))
;;       ]
;;
;;   (no-fail
;;     (shutdown foo))
;;   (if (error? last)
;;     (throw (error last))
;;     (value last)))
;; ```
;;

;; the first step is to separate the expressions fromt he finalizer (if present).
;; And the test info map


(defn extract-finalizer
  "returns [expressions finalizer]"
  [forms]
  (let [[expressions _ & finalizer] (partition-by #{:rdt/finalize :rdt/finalise} forms)]
    [expressions (mapcat identity finalizer)]))



(defn extract-test-info
  "returns [cfg expressions finalizer]"
  [forms captured-ns captured-form]
  (let [cfg       (if (map?    (first forms)) (first forms) {})
        forms     (if (map?    (first forms)) (rest forms) forms)
        test-name (if (string? (first forms)) (first forms) "REPL tests")
        forms     (if (string? (first forms)) (rest forms) forms)
        [expressions finalizer] (extract-finalizer forms)
        {:keys [line column]} (meta captured-form)
        site      (str *ns* "[l:" line ", c:" column "]")
        id        (test-id captured-form)
        cfg       (assoc cfg :id id :ns (str captured-ns) :form (list `quote captured-form)
                    :name test-name
                    :location site)]
    [cfg expressions finalizer])
  )



(comment

  (defmacro dummy [& forms]
    `(do ~(extract-test-info forms *ns* &form)))

  (dummy {:labels [:bar]} "foo"

    (def foo (make-foo 1 2 3))                 ;; def expression
    (foo 1) => {:some-val 100}                 ;; checking expression

    :rdt/finalize
    (shutdown foo)                             ;; finalizer
    )                                          ;; to be run whether the test pass or fail

  )



;;
;; The second step is to separate the expressions into triples of the form
;; `[expression => value]` if there is a checking arrow. if there is no
;; checking arrow then the expression is simply `[expression]`
;;
;;


(defn -separate-expressions
  [body]
  (->>
    (concat body [:rdt/void])
    ;; separate statements
    (partition 3 1 '(:rdt/void :rdt/void :rdt/void))
    (reduce (fn [state [left test right :as triplet]]
         (cond
           (pos? (:drop state)) (update state :drop dec)
           (#{'=> '==>} test)   (-> state (update :forms conj (vec triplet)) (update :drop + 2))
           :else                (update state :forms conj [left])))
      {:forms [] :drop 0})
    ;; did I miss something?
    (#(if (pos? (:drop %))
        (throw (ex-info "Missing value after checking arrows" {:form (last (:forms %))}))
        %))
    ;; return statements
    :forms))



(comment
  (-separate-expressions
    '(:ok
      (def foo 1)
      (def bar 2)

      (println foo)
      (println bar)

      (+ foo bar)  => 3

      (def foo 2)
      (println (+ foo bar))

      (assoc {} :foo (+ foo bar) :bar 22)
      => {:foo 4 }

      (println "end"))))



;;
;; Next step is to turn expressions into a map which contains all the information about the
;; test, the check and the actual expression.
;;

(defn checker
  [[left test right :as triplet]]
  (cond
    (nil? test)
    nil

    (and (list? right) (= 'throws (first right)))
    ['throws `chk/throws-checker]

    (= test '=>)
    ['=> `chk/fuzzy-checker]

    (= test '==>)
    ['==> `chk/exact-checker]

    :else
    (throw
      (ex-info (format "Unknown or invalid checker %s" (pr-str test))
        {:form triplet}))))



(defn -expressions->meta-expr
  [test-sym expressions]
  (let [_last (gensym "_last_")
        def?  (where [:and [list? :is? true] [first :is? 'def]])
        defn? (where [:and [list? :is? true] [first :is? 'defn]])
        as-tunk (fn [expression] `(fn [] ~expression))]
    (->> expressions
      (mapv (fn [index [left test right :as triplet]]
              (let [checkable? (not (nil? test))
                    checker* (checker triplet)
                    meta {:form (list `quote (vec triplet))
                          :index index
                          :checkable? checkable?
                          :defx?  (when (or (def? left) (defn? left)) (second left))
                          :checking-symbol (list `quote (first checker*))
                          :checking-funciton (second checker*)
                          :expression (cond
                                        (def? left)
                                        (as-tunk (last left))

                                        (defn? left)
                                        (as-tunk (cons `fn (rest left)))

                                        (and checkable? (= 'throws (first checker*)))
                                        (as-tunk `(~(second checker*) ~(second right) (fn [] ~left)))

                                        checkable?
                                        (as-tunk `(~(second checker*) ~right (fn [] ~left)))

                                        :else ;; simple expression
                                        (as-tunk left))
                          :test test-sym}]

                meta)) (range)))))



(comment
  (->> '((println "hello")
       (def foo 1)
       (+ 2 foo)  => 3)
    (-separate-expressions)
    (-expressions->meta-expr 'my-test)
    )

  (->> '((/ 1 0)  => (throws Exception))
    (-separate-expressions)
    (-expressions->meta-expr 'my-test)
    )
  )



;;
;;
;; Now, before the final step we need to enrich the tests by adding
;; the runtime wrappers around the test and all expressions.
;; wrappers are used to record stats and report


(defn -wrap-expressions
  [expr-sym expressions]
  (->> expressions
    (map (fn [{:keys [expression] :as expr}]
           (assoc expr :expression (list `-wrap-expression `*runner* expr-sym expression))))))



(defn -wrap-expression
  [runner meta expression]
  (if-let [wrapper (:rdt/expression-wrapper runner)]
    (wrapper meta expression)
    expression))



(defn -wrap-test-finalizer
  [runner test-info finalizer]
  (if-let [wrapper (:rdt/finalizer-wrapper runner)]
    (wrapper test-info finalizer)
    finalizer))



(defn -wrap-test
  [runner test-info test]
  (if-let [wrapper (:rdt/test-wrapper runner)]
    (wrapper test-info test)
    test))



(comment
  (->> '((println "hello")
       (def foo 1)
       (+ 2 foo)  => 3)
    (-separate-expressions)
    (-expressions->meta-expr 'my-test)
    (-wrap-expressions 'expr)
    )

  )



;;
;; Finally, expanding the expressions into a let binding
;;

;; map accessors
(def error? :error)



(def error  :error)



(def value  :result)



(defn -expand-expression
  [last-sym expr-sym try-sym {:keys [defx? expression] :as expr}]
  [expr-sym (-> expr (dissoc :expression) (update :defx? (fn [sym] (and sym (list `quote sym)))))
   try-sym  `(fn [] (try (assoc ~expr-sym :result (~expression))
                        (catch Exception x# (assoc ~expr-sym :error x#))))
   (if defx? {defx? :result :as last-sym} last-sym) `(if (error? ~last-sym) ~last-sym (~try-sym))]
  )



(defn -expand-expressions
  [_test _last _expr _try-exp expressions]
  (->> expressions
    (-separate-expressions)
    (-expressions->meta-expr _test)
    (-wrap-expressions _expr)
    (mapcat (partial -expand-expression _last _expr _try-exp)))
  )



(comment
  (->> '((println "hello")
       (def foo 1)
       (+ 2 foo)  => 3)
    (-expand-expressions {} 'last 'expr 'try-expr))

  )



(defn -generate-prehamble
  [last-sym test-sym test-info]
  [test-sym test-info
   last-sym nil])



(defn -generate-let-body
  [last-sym _test-sym finalizer-expr]
  `(do
     ;; run finalizer if present
     (no-fail
       ((-wrap-test-finalizer ~`*runner* ~_test-sym (thunk ~@finalizer-expr))))
     ;; return result
     (if (error? ~last-sym)
       (throw (error ~last-sym))
       (value ~last-sym))))



(defn -generate-let
  [test-info expressions finalizer]
  (let [_test      (gensym "_test_")
        _last      (gensym "_last_")
        _expr      (gensym "_expr_")
        _try-expr  (gensym "_try-expr_")]
    `(let [;; prehamble
           ~@(-generate-prehamble _last _test test-info)

           ;; expressions
           ~@(-expand-expressions _test _last _expr _try-expr expressions)]

       ;; finalizer and result
       ~(-generate-let-body _last _test finalizer))))



(comment
  (-generate-let
    {:name "test foo"}
    '((println "hello")
      (def foo 1)
      (+ 2 foo)  => 3)
    '((println "done")))

  )



(defn generate-test-function
  [forms captured-ns captured-form]
  (let [[test-info expressions finalizer] (extract-test-info forms captured-ns captured-form)]
    `(fn ~'this-test
       ([cmd#]
        (case cmd#
          :test-id   ~(:id test-info)
          :test-info ~test-info))
       ([]
        ((-wrap-test ~`*runner* (~'this-test :test-info)
           (fn []
             ~(-generate-let test-info expressions finalizer))))))))



(comment

  (defmacro repl-test
    [& forms]
    `(~(generate-test-function forms *ns* &form)))


  (repl-test "testing addition"

    (+ 1 1) =>  2
    :rdt/finalize
    (println "DONE")
    )

  (repl-test
    (def foo 1)
    (+ foo 1) =>  2)


  (repl-test
    (defn foo [n] (* n 2))
    (foo 3) => 6)

  (repl-test
    (def foo 1))

  (repl-test
    (+ 1 2) => 3.0)

  (repl-test
    (+ 1 2) ==> 3.0)

  (repl-test
    :ok
    (def foo 1)
    (def bar 2)

    (println foo)
    (println bar)

    (+ foo bar)  => 3

    ;;(/ foo 0)

    (def foo 2)
    (println (+ foo bar))

    (assoc {} :foo (+ foo bar) :bar 22)
    => {:foo 4 }

    (println "end")

    )


  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                        ----==| R U N N E R |==----                         ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(def runner nil)  ;; for repl development



(defmulti runner (fn [{:keys [type]} test] type))



(defmethod runner nil
  [_ test])



(defmethod runner :inline
  [_ test]
  (test))



(defmethod runner :rdt/test-runner
  [_ test]
  test)



(defn eval-test
  [test]
  (runner *runner* test))
