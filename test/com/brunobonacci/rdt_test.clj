(ns com.brunobonacci.rdt-test
  (:require [com.brunobonacci.rdt :refer :all]
            [midje.sweet :refer :all]))


(fact "is it cool?"
      (foo) => "do something cool")
