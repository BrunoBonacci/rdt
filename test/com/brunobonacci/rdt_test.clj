(ns com.brunobonacci.rdt-test
  (:require [com.brunobonacci.rdt :refer :all]
            [com.brunobonacci.rdt.checkers :as chk]))



(repl-test "testing checkers"


  1 => 1
  1 ==> 1
  1.0 => 1

  ;; 1.0 ==> 1  ;; this is false

  1.0 ==> 1.0

  [1 2 3 4] => [1]

  (/ 1 0) => (throws ArithmeticException)

  (/ 1 0) => (throws Exception)
  )



;; import private fn
(def partial-matcher #'chk/partial-matcher)



(repl-test

  (partial-matcher 3 3) ==> true
  (partial-matcher 3 4) ==> (throws Exception)
  (partial-matcher :a :a) ==> true
  (partial-matcher false false) ==> true
  (partial-matcher "foo" "foo") ==> true
  (partial-matcher nil nil) ==> true
  (partial-matcher 'foo 'foo) ==> true
  (partial-matcher \c \c) ==> true
  (partial-matcher 1.0 1) ==> true
  (partial-matcher 1 1.0) ==> true

  (partial-matcher [] []) ==> true
  (partial-matcher [1] [1]) ==> true
  (partial-matcher [2] [1]) ==> (throws Exception)
  (partial-matcher [1 2 3] [1 2 3]) ==> true
  (partial-matcher [1 2 3] [1 2 :x]) ==> (throws Exception)
  (partial-matcher [1 2 3] [1 2 3 4]) ==> true
  (partial-matcher [1 2 3 4] [1 2 3]) ==> (throws Exception)

  (partial-matcher '() '()) ==> true
  (partial-matcher '(1) '(1)) ==> true
  (partial-matcher '(2) '(1)) ==> (throws Exception)
  (partial-matcher '(1 2 3) '(1 2 3)) ==> true
  (partial-matcher '(1 2 3) '(1 2 :x)) ==> (throws Exception)
  (partial-matcher '(1 2 3) '(1 2 3 4)) ==> true
  (partial-matcher '(1 2 3 4) '(1 2 3)) ==> (throws Exception)

  (partial-matcher '(1 2 3) [1 2 3]) ==> true
  (partial-matcher [1 2 3] '(1 2 3)) ==> true

  (partial-matcher #{} #{}) ==> true
  (partial-matcher #{1} #{1}) ==> true
  (partial-matcher #{1} #{2}) ==> (throws Exception)
  (partial-matcher #{1 2} #{1 2 3 4}) ==> true
  (partial-matcher #{1 2 3 4} #{1 2}) ==> (throws Exception)

  (partial-matcher [] (byte-array [])) ==> true
  (partial-matcher [1] (byte-array [1])) ==> true
  (partial-matcher [2] (byte-array [1])) ==> (throws Exception)
  (partial-matcher [1 2 3] (byte-array [1 2 3])) ==> true
  (partial-matcher [1 2 3] (byte-array [1 2 3 4])) ==> true
  (partial-matcher [1 2 3 4] (byte-array [1 2 3])) ==> (throws Exception)

  (partial-matcher [] (short-array [])) ==> true
  (partial-matcher [1] (short-array [1])) ==> true
  (partial-matcher [2] (short-array [1])) ==> (throws Exception)
  (partial-matcher [1 2 3] (short-array [1 2 3])) ==> true
  (partial-matcher [1 2 3] (short-array [1 2 3 4])) ==> true
  (partial-matcher [1 2 3 4] (short-array [1 2 3])) ==> (throws Exception)

  (partial-matcher [] (int-array [])) ==> true
  (partial-matcher [1] (int-array [1])) ==> true
  (partial-matcher [2] (int-array [1])) ==> (throws Exception)
  (partial-matcher [1 2 3] (int-array [1 2 3])) ==> true
  (partial-matcher [1 2 3] (int-array [1 2 3 4])) ==> true
  (partial-matcher [1 2 3 4] (int-array [1 2 3])) ==> (throws Exception)

  (partial-matcher [] (long-array [])) ==> true
  (partial-matcher [1] (long-array [1])) ==> true
  (partial-matcher [2] (long-array [1])) ==> (throws Exception)
  (partial-matcher [1 2 3] (long-array [1 2 3])) ==> true
  (partial-matcher [1 2 3] (long-array [1 2 3 4])) ==> true
  (partial-matcher [1 2 3 4] (long-array [1 2 3])) ==> (throws Exception)

  (partial-matcher [] (float-array [])) ==> true
  (partial-matcher [1.0] (float-array [1])) ==> true
  (partial-matcher [2.0] (float-array [1])) ==> (throws Exception)
  (partial-matcher [1.0 2.0 3.0] (float-array [1 2 3])) ==> true
  (partial-matcher [1.0 2.0 3.0] (float-array [1 2 3 4])) ==> true
  (partial-matcher [1.0 2.0 3.0 4.0] (float-array [1 2 3])) ==> (throws Exception)

  (partial-matcher [] (double-array [])) ==> true
  (partial-matcher [1.0] (double-array [1])) ==> true
  (partial-matcher [2.0] (double-array [1])) ==> (throws Exception)
  (partial-matcher [1.0 2.0 3.0] (double-array [1 2 3])) ==> true
  (partial-matcher [1.0 2.0 3.0] (double-array [1 2 3 4])) ==> true
  (partial-matcher [1.0 2.0 3.0 4.0] (double-array [1 2 3])) ==> (throws Exception)

  (partial-matcher [] (char-array [])) ==> true
  (partial-matcher [\a] (char-array [\a])) ==> true
  (partial-matcher [\b] (char-array [\a])) ==> (throws Exception)
  (partial-matcher [\a \b \c] (char-array [\a \b \c])) ==> true
  (partial-matcher [\a \b \c] (char-array [\a \b \c \d])) ==> true
  (partial-matcher [\a \b \c \d] (char-array [\a \b \c])) ==> (throws Exception)

  (partial-matcher [] (boolean-array [])) ==> true
  (partial-matcher [true] (boolean-array [true])) ==> true
  (partial-matcher [false] (boolean-array [true])) ==> (throws Exception)
  (partial-matcher [true false] (boolean-array [true false])) ==> true
  (partial-matcher
    [true false true false]
    (boolean-array [true false true])) ==> (throws Exception)

  (partial-matcher [] (object-array [])) ==> true
  (partial-matcher [1] (object-array [1])) ==> true
  (partial-matcher [2] (object-array [1])) ==> (throws Exception)
  (partial-matcher [1 2 3] (object-array [1 2 3])) ==> true
  (partial-matcher [1 2 3] (object-array [1 2 :x])) ==> (throws Exception)
  (partial-matcher [1 2 3] (object-array [1 2 3 4])) ==> true
  (partial-matcher [1 2 3 4] (object-array [1 2 3])) ==> (throws Exception)

  (partial-matcher {:s 2} {:s 2}) ==> true
  (partial-matcher {:s 2 :b 3} {:s 2 :b 3}) ==> true
  (partial-matcher {:s 2 :b 3} {:s 2 :b 3 :x 4}) ==> true
  (partial-matcher {:s 2 :b 3 :c 4} {:s 2 :b 3}) ==> (throws Exception)

  (partial-matcher number? 3) ==> true
  (partial-matcher odd? 3) ==> true
  (partial-matcher odd? 2) ==> (throws Exception)

  (partial-matcher #"foo" "foo") ==> true
  (partial-matcher #"foo" "foobar") ==> (throws Exception)
  (partial-matcher #"foo.*" "foobar") ==> true
  (partial-matcher #"foo" nil) ==> (throws Exception)

  (partial-matcher
    {:l [number? {:foo odd? :bar #{1 2}}]}
    {:l [1 {:foo 1 :bar #{1 2 3}}] :z 4}) ==> true
  )



(repl-test "testing repl-test macro"

  (->>
    '(repl-test
       (+ 1 1) => 2)
    (macroexpand-1)
    (tree-seq sequential? identity)
    (filter #(and (sequential? %) (= (first %) 'com.brunobonacci.rdt.internal/fact->checks)))
    first)
  => (list 'com.brunobonacci.rdt.internal/fact->checks any? '((+ 1 1) => 2) ())

  )




(repl-test "testing repl-test macro"

  (->>
    '(repl-test "adding test name"
       (+ 1 1) => 2)
    (macroexpand-1)
    (tree-seq sequential? identity)
    (filter #(and (sequential? %) (= (first %) 'com.brunobonacci.rdt.internal/fact->checks)))
    first)
  => (list 'com.brunobonacci.rdt.internal/fact->checks any? '((+ 1 1) => 2) ())


  (->>
    '(repl-test "adding test name"
       (+ 1 1) => 2)
    (macroexpand-1)
    (tree-seq sequential? identity)
    (filter #(and (map? %) (:id %)))
    first
    :name)
  => "adding test name"
  )



(repl-test "testing repl-test macro"

  (->>
    '(repl-test {:labels [:foo :bar]} "adding labels"
       (+ 1 1) => 2)
    (macroexpand-1)
    (tree-seq sequential? identity)
    (filter #(and (map? %) (:labels %)))
    first
    :labels)
  => [:foo :bar]
  )



(repl-test "testing repl-test macro with finalizers"

  (->>
    '(repl-test
       (+ 1 1) => 2
       :rdt/finalize
       (println "all done!"))
    (macroexpand-1)
    (tree-seq sequential? identity)
    (filter #(and (sequential? %) (= (first %) 'com.brunobonacci.rdt.internal/fact->checks)))
    first)
  => (list 'com.brunobonacci.rdt.internal/fact->checks
      any?
      '((+ 1 1) => 2)
      '((println "all done!")))




  ;; if more than one :rdt/finalize are added it grabs them all
  (->> '(repl-test
        (+ 1 1) => 2
        :rdt/finalize
        (println "all done!")
        :rdt/finalize
        (println "all done!2"))
    (macroexpand-1)
    (tree-seq sequential? identity)
    (filter #(and (sequential? %) (= (first %) 'com.brunobonacci.rdt.internal/fact->checks)))
    first)
  => (list 'com.brunobonacci.rdt.internal/fact->checks
      any?
      '((+ 1 1) => 2)
      '((println "all done!") :rdt/finalize (println "all done!2")))

  )



(repl-test "testing finalizers execution, no headline"

  (def finalizer (atom false))

  (repl-test
    (+ 1 1) ==> 2

    :rdt/finalize
    (swap! finalizer (constantly true)))

  @finalizer ==> true

  )



(repl-test "testing finalizers execution, with headline"

  (def finalizer (atom false))

  (repl-test "testing finalizer"
    (+ 1 1) ==> 2

    :rdt/finalize
    (swap! finalizer (constantly true)))

  @finalizer ==> true

  )



(repl-test "testing finalizers execution, with labels"

  (def finalizer (atom false))

  (repl-test {:labels [:foo]} "testing finalizer"
    (+ 1 1) ==> 2

    :rdt/finalize
    (swap! finalizer (constantly true)))

  @finalizer ==> true

  )
