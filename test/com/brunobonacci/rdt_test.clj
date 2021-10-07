(ns com.brunobonacci.rdt-test
  (:require [com.brunobonacci.rdt :refer :all]))



;; import private fn
(def subset-matcher #'com.brunobonacci.rdt/subset-matcher)


(repl-test

  (subset-matcher 3 3) ==> true
  (subset-matcher 3 4) ==> (throws Exception)
  (subset-matcher :a :a) ==> true
  (subset-matcher false false) ==> true
  (subset-matcher "foo" "foo") ==> true
  (subset-matcher nil nil) ==> true
  (subset-matcher 'foo 'foo) ==> true
  (subset-matcher \c \c) ==> true

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
