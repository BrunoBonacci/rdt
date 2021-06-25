(ns perf
  (:require [com.brunobonacci.rdt :refer :all]
            [criterium.core :refer [bench quick-bench]]
            [clj-async-profiler.core :as prof]
            [jmh.core :as jmh]
            [clojure.edn :as edn]))


(comment

  ;; perf tests

  (bench (Thread/sleep 1000))

  )


(comment

  (prof/profile
    (bench (foo)))
  )


(comment

  ;; Run JMH benchmarks
  (jmh/run
    (edn/read-string (slurp "./dev/perf/benchmarks.edn"))
    {:type  :quick
     :status true
     :pprint true})
  )
