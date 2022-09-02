(ns com.brunobonacci.rdt.utils-test
  (:require [com.brunobonacci.rdt.utils :as ut]
            [com.brunobonacci.rdt :refer :all]))



(repl-test "serialize and deserialize data that can't be freezed with nippy"

  (-> {:color (java.awt.Color. 1 2 3)}
    ut/serialize
    ut/deserialize)
  => {:color string?}

  )
