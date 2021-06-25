(ns perf.benchmarks
  (:require [com.brunobonacci.rdt :refer :all]))


;; write your test benchmarks here:
(defn test-sleep
  []
  (Thread/sleep 1))
