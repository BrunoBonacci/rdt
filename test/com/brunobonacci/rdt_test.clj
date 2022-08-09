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
(def subset-matcher #'chk/subset-matcher)



(repl-test

  (subset-matcher 3 3) ==> true
  (subset-matcher 3 4) ==> (throws Exception)
  (subset-matcher :a :a) ==> true
  (subset-matcher false false) ==> true
  (subset-matcher "foo" "foo") ==> true
  (subset-matcher nil nil) ==> true
  (subset-matcher 'foo 'foo) ==> true
  (subset-matcher \c \c) ==> true
  (subset-matcher 1.0 1) ==> true
  (subset-matcher 1 1.0) ==> true

  (subset-matcher [] []) ==> true
  (subset-matcher [1] [1]) ==> true
  (subset-matcher [2] [1]) ==> (throws Exception)
  (subset-matcher [1 2 3] [1 2 3]) ==> true
  (subset-matcher [1 2 3] [1 2 :x]) ==> (throws Exception)
  (subset-matcher [1 2 3] [1 2 3 4]) ==> true
  (subset-matcher [1 2 3 4] [1 2 3]) ==> (throws Exception)

  (subset-matcher '() '()) ==> true
  (subset-matcher '(1) '(1)) ==> true
  (subset-matcher '(2) '(1)) ==> (throws Exception)
  (subset-matcher '(1 2 3) '(1 2 3)) ==> true
  (subset-matcher '(1 2 3) '(1 2 :x)) ==> (throws Exception)
  (subset-matcher '(1 2 3) '(1 2 3 4)) ==> true
  (subset-matcher '(1 2 3 4) '(1 2 3)) ==> (throws Exception)

  (subset-matcher '(1 2 3) [1 2 3]) ==> true
  (subset-matcher [1 2 3] '(1 2 3)) ==> true

  (subset-matcher #{} #{}) ==> true
  (subset-matcher #{1} #{1}) ==> true
  (subset-matcher #{1} #{2}) ==> (throws Exception)
  (subset-matcher #{1 2} #{1 2 3 4}) ==> true
  (subset-matcher #{1 2 3 4} #{1 2}) ==> (throws Exception)

  (subset-matcher [] (byte-array [])) ==> true
  (subset-matcher [1] (byte-array [1])) ==> true
  (subset-matcher [2] (byte-array [1])) ==> (throws Exception)
  (subset-matcher [1 2 3] (byte-array [1 2 3])) ==> true
  (subset-matcher [1 2 3] (byte-array [1 2 3 4])) ==> true
  (subset-matcher [1 2 3 4] (byte-array [1 2 3])) ==> (throws Exception)

  (subset-matcher [] (short-array [])) ==> true
  (subset-matcher [1] (short-array [1])) ==> true
  (subset-matcher [2] (short-array [1])) ==> (throws Exception)
  (subset-matcher [1 2 3] (short-array [1 2 3])) ==> true
  (subset-matcher [1 2 3] (short-array [1 2 3 4])) ==> true
  (subset-matcher [1 2 3 4] (short-array [1 2 3])) ==> (throws Exception)

  (subset-matcher [] (int-array [])) ==> true
  (subset-matcher [1] (int-array [1])) ==> true
  (subset-matcher [2] (int-array [1])) ==> (throws Exception)
  (subset-matcher [1 2 3] (int-array [1 2 3])) ==> true
  (subset-matcher [1 2 3] (int-array [1 2 3 4])) ==> true
  (subset-matcher [1 2 3 4] (int-array [1 2 3])) ==> (throws Exception)

  (subset-matcher [] (long-array [])) ==> true
  (subset-matcher [1] (long-array [1])) ==> true
  (subset-matcher [2] (long-array [1])) ==> (throws Exception)
  (subset-matcher [1 2 3] (long-array [1 2 3])) ==> true
  (subset-matcher [1 2 3] (long-array [1 2 3 4])) ==> true
  (subset-matcher [1 2 3 4] (long-array [1 2 3])) ==> (throws Exception)

  (subset-matcher [] (float-array [])) ==> true
  (subset-matcher [1.0] (float-array [1])) ==> true
  (subset-matcher [2.0] (float-array [1])) ==> (throws Exception)
  (subset-matcher [1.0 2.0 3.0] (float-array [1 2 3])) ==> true
  (subset-matcher [1.0 2.0 3.0] (float-array [1 2 3 4])) ==> true
  (subset-matcher [1.0 2.0 3.0 4.0] (float-array [1 2 3])) ==> (throws Exception)

  (subset-matcher [] (double-array [])) ==> true
  (subset-matcher [1.0] (double-array [1])) ==> true
  (subset-matcher [2.0] (double-array [1])) ==> (throws Exception)
  (subset-matcher [1.0 2.0 3.0] (double-array [1 2 3])) ==> true
  (subset-matcher [1.0 2.0 3.0] (double-array [1 2 3 4])) ==> true
  (subset-matcher [1.0 2.0 3.0 4.0] (double-array [1 2 3])) ==> (throws Exception)

  (subset-matcher [] (char-array [])) ==> true
  (subset-matcher [\a] (char-array [\a])) ==> true
  (subset-matcher [\b] (char-array [\a])) ==> (throws Exception)
  (subset-matcher [\a \b \c] (char-array [\a \b \c])) ==> true
  (subset-matcher [\a \b \c] (char-array [\a \b \c \d])) ==> true
  (subset-matcher [\a \b \c \d] (char-array [\a \b \c])) ==> (throws Exception)

  (subset-matcher [] (boolean-array [])) ==> true
  (subset-matcher [true] (boolean-array [true])) ==> true
  (subset-matcher [false] (boolean-array [true])) ==> (throws Exception)
  (subset-matcher [true false] (boolean-array [true false])) ==> true
  (subset-matcher
    [true false true false]
    (boolean-array [true false true])) ==> (throws Exception)

  (subset-matcher [] (object-array [])) ==> true
  (subset-matcher [1] (object-array [1])) ==> true
  (subset-matcher [2] (object-array [1])) ==> (throws Exception)
  (subset-matcher [1 2 3] (object-array [1 2 3])) ==> true
  (subset-matcher [1 2 3] (object-array [1 2 :x])) ==> (throws Exception)
  (subset-matcher [1 2 3] (object-array [1 2 3 4])) ==> true
  (subset-matcher [1 2 3 4] (object-array [1 2 3])) ==> (throws Exception)

  (subset-matcher {:s 2} {:s 2}) ==> true
  (subset-matcher {:s 2 :b 3} {:s 2 :b 3}) ==> true
  (subset-matcher {:s 2 :b 3} {:s 2 :b 3 :x 4}) ==> true
  (subset-matcher {:s 2 :b 3 :c 4} {:s 2 :b 3}) ==> (throws Exception)

  (subset-matcher number? 3) ==> true
  (subset-matcher odd? 3) ==> true
  (subset-matcher odd? 2) ==> (throws Exception)

  (subset-matcher #"foo" "foo") ==> true
  (subset-matcher #"foo" "foobar") ==> (throws Exception)
  (subset-matcher #"foo.*" "foobar") ==> true
  (subset-matcher #"foo" nil) ==> (throws Exception)

  (subset-matcher
    {:l [number? {:foo odd? :bar #{1 2}}]}
    {:l [1 {:foo 1 :bar #{1 2 3}}] :z 4}) ==> true
  )



(repl-test "testing repl-test macro"

  (macroexpand-1
    '(repl-test
       (+ 1 1) => 2))
  => '(com.brunobonacci.rdt.internal/register-and-run
       "97c0cfc9cda15fee776817c4c649c46daeaa58014e892c913afacbab4ddfc7ff"
       {:id "97c0cfc9cda15fee776817c4c649c46daeaa58014e892c913afacbab4ddfc7ff",
        :ns "com.brunobonacci.rdt-test",
        :form '(repl-test (+ 1 1) => 2),
        :name "REPL tests",
        :outcome nil}
       (clojure.core/fn
         []
         (com.brunobonacci.rdt.internal/fact->checks ((+ 1 1) => 2) ())))

  )



(repl-test "testing repl-test macro"

  (macroexpand-1
    '(repl-test "adding test name"
       (+ 1 1) => 2))
  => '(com.brunobonacci.rdt.internal/register-and-run
       "bef77381d5b8b0a47dd7e790df9fd04b699849342475237f40da871a69d8174d"
       {:id "bef77381d5b8b0a47dd7e790df9fd04b699849342475237f40da871a69d8174d",
        :ns "com.brunobonacci.rdt-test",
        :form '(repl-test "adding test name" (+ 1 1) => 2),
        :name "adding test name",
        :outcome nil}
       (clojure.core/fn
         []
         (com.brunobonacci.rdt.internal/fact->checks ((+ 1 1) => 2) ())))

  )



(repl-test "testing repl-test macro"

  (macroexpand-1
    '(repl-test {:labels [:foo :bar]} "adding labels"
       (+ 1 1) => 2))
  => '(com.brunobonacci.rdt.internal/register-and-run
       "558303fc5daceaebfd7f04599024714d1d6b8c390ce04b115e7b7b84174d1ecc"
       {:labels [:foo :bar],
        :id "558303fc5daceaebfd7f04599024714d1d6b8c390ce04b115e7b7b84174d1ecc",
        :ns "com.brunobonacci.rdt-test",
        :form '(repl-test {:labels [:foo :bar]} "adding labels" (+ 1 1) => 2),
        :name "adding labels",
        :outcome nil}
       (clojure.core/fn
         []
         (com.brunobonacci.rdt.internal/fact->checks ((+ 1 1) => 2) ())))
  )



(repl-test "testing repl-test macro"
  (macroexpand-1
    '(repl-test "different checkers"
       [1 2 3 4] =>  [1 2]
       [1 2 3 4] ==> [1 2 3 4]))
  => '(com.brunobonacci.rdt.internal/register-and-run
       "644678c04ef05eb8d254d08c6cebc7a68c76f6069e3abf06c1df9725b421a7aa"
       {:id "644678c04ef05eb8d254d08c6cebc7a68c76f6069e3abf06c1df9725b421a7aa",
        :ns "com.brunobonacci.rdt-test",
        :form
        '(repl-test
           "different checkers"
           [1 2 3 4]
           =>
           [1 2]
           [1 2 3 4]
           ==>
           [1 2 3 4]),
        :name "different checkers",
        :outcome nil}
       (clojure.core/fn
         []
         (com.brunobonacci.rdt.internal/fact->checks
           ([1 2 3 4] => [1 2] [1 2 3 4] ==> [1 2 3 4])
           ())))

  )



(repl-test "testing repl-test macro with finalizers"

  (macroexpand-1
    '(repl-test
       (+ 1 1) => 2
       :rdt/finalize
       (println "all done!")))
  => '(com.brunobonacci.rdt.internal/register-and-run
       "1cc313d7deee57a8c6c9258cba5b225605d874bf81e0a3f0bc5dc2dd60f0389b"
       {:ns "com.brunobonacci.rdt-test",
        :name "REPL tests",
        :outcome nil}
       (clojure.core/fn
         []
         (com.brunobonacci.rdt.internal/fact->checks
           ((+ 1 1) => 2)
           ((println "all done!")))))




  ;; if more than one :rdt/finalize are added it grabs them all
  (macroexpand-1
    '(repl-test
       (+ 1 1) => 2
       :rdt/finalize
       (println "all done!")
       :rdt/finalize
       (println "all done!2")))
  => '(com.brunobonacci.rdt.internal/register-and-run
       "5935fd0dd9788e32bb16520d13cbfabf924fcb15ef4716e70cc76c29d018a4fc"
       {:ns "com.brunobonacci.rdt-test",
        :name "REPL tests",
        :outcome nil}
       (clojure.core/fn
         []
         (com.brunobonacci.rdt.internal/fact->checks
           ((+ 1 1) => 2)
           ((println "all done!") :rdt/finalize (println "all done!2")))))

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
