(ns com.brunobonacci.rdt.internal
  (:require [where.core :refer [where]]
            [com.brunobonacci.rdt.checkers :as chk]
            [clojure.string :as str]
            [com.brunobonacci.mulog :as u]))



(def ^:dynamic *runner*
  {:type :inline :reporters [] :include-labels :all :exclude-labels nil
   :expression-wrapper (fn [meta expression] expression)})



(defmacro no-fail
  [& body]
  `(try ~@body (catch Exception x#)))



(defmacro do-with
  [value & forms]
  `(let [~'it ~value]
     (no-fail ~@forms)
     ~'it))



(defmacro do-with-exception
  [value ok fail]
  `(let [[~'it ~'error] (try [(do ~value) nil] (catch Exception x# [nil x#]))]
     (if ~'error
       (do (no-fail ~fail) (throw ~'error))
       (do (no-fail ~ok)   ~'it))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                ----==| T E R M   R E W R I T I N G |==----                 ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn -separate-statements
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
  (-separate-statements
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



(defn execute-expression
  [{:keys [expression-wrapper] :as cfg} [_ last-error :as last] expression* meta]
  (if last-error
    last
    (let [expression (if expression-wrapper (expression-wrapper meta expression*) expression*)]
      (try
        [(expression) nil meta]
        (catch Exception x
          [nil x meta])))))



(defn -wrap-statement
  [config last-sym meta forms]
  `(execute-expression ~config ~last-sym (fn [] ~forms) ~meta))



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



(defn -statements->executable
  [test-info statements]
  (let [_last (gensym "_last_")
        def?  (where [:and [list? :is? true] [first :is? 'def]])
        defn? (where [:and [list? :is? true] [first :is? 'defn]])]
    (->> statements
      (mapv (fn [[left test right :as triplet]]
              (let [checkable? (not (nil? test))
                    checker* (checker triplet)
                    meta {:form (list `quote (vec triplet))
                          :checkable? checkable?
                          :def?  (def? left)
                          :defn? (defn? left)
                          :checking-symbol (list `quote (first checker*))
                          :checking-funciton (second checker*)
                          :test test-info}]

                (cond
                  (def? left)
                  [[(second left) :as _last] (-wrap-statement *runner* _last meta (last left))]

                  (defn? left)
                  [[(second left) :as _last] (-wrap-statement *runner* _last meta (cons `fn (rest left)))]

                  (and checkable? (= 'throws (first checker*)))
                  [_last (-wrap-statement *runner* _last meta `(~(second checker*) ~(second right) (fn [] ~left)))]

                  checkable?
                  [_last (-wrap-statement *runner* _last meta `(~(second checker*) ~right (fn [] ~left)))]

                  :else ;; statement
                  [_last (-wrap-statement *runner* _last meta left)]))
              ))
      (#(conj % [_last `(if (second ~_last) (throw (second ~_last)) (first ~_last))]))
      (cons [_last nil]))))



(comment
  (->> '((+ 2 1)  => 3)
    (-separate-statements)
    (-statements->executable {})
    )

  (->> '((/ 1 0)  => (throws Exception))
    (-separate-statements)
    (-statements->executable {})
    )
  )



(defn -fact->checks
  {:no-doc true}
  [test-info body final]
  (->> body
    (-separate-statements)
    (-statements->executable test-info)
    ((fn [statements]
       `(let ~(vec (mapcat identity (drop-last 1 statements)))
          ;; finalizer
          (try ~@final (catch Exception fx#
                         ;; TODO: use mulog
                         (println "Failed to execute finalizer due to" (ex-message fx#))))
          ;; last statement value
          ~(->> statements (take-last 1) first second))))))



(defmacro fact->checks
  {:no-doc true}
  [test-info body final]
  (-fact->checks test-info body final))



(defmacro fact->checks2
  {:no-doc true}
  [& body]
  (-fact->checks nil body '()))



(comment


  (fact->checks2
    (defn foo [n] (* n 2))
    (foo 3) => 6)

  (fact->checks2
    (def foo 1))

  (fact->checks2
    (+ 1 2) => 3.0)

  (fact->checks2
    (+ 1 2) ==> 3.0)

  (fact->checks2
    :ok
    (def foo 1)
    (def bar 2)

    (println foo)
    (println bar)

    (+ foo bar)  => 3
    (/ foo 0)
    (def foo 2)
    (println (+ foo bar))

    (assoc {} :foo (+ foo bar) :bar 22)
    => {:foo 4 }

    (println "end")

    )


  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                      ----==| R E G I S T R Y |==----                       ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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



(def runner nil)



(defmulti runner (fn [{:keys [type]} test] type))



(defmethod runner nil
  [_ test])



(defmethod runner :inline
  [_ test]
  (test))



(defn eval-test
  [test]
  (runner *runner* test))
