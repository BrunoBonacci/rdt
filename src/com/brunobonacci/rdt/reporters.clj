(ns com.brunobonacci.rdt.reporters
  (:require [clojure.string :as str]
            [com.brunobonacci.rdt.utils :as ut]))


(def reporter-factory nil)  ;; for repl development



(defmulti reporter-factory (fn [{:keys [name]}] name))



(defn compile-reporters
  [reporters]
  (fn [execution-stats]
    (->> reporters
      (map (fn [r] (if (keyword? r) {:name r} r)))
      (map reporter-factory)
      (run! (fn [r] (r execution-stats))))))



(defn brief-summary
  [execution-stats]
  (let [tests       (reduce + (map #(:executions % 0)  (vals execution-stats)))
        success     (reduce + (map #(:success % 0)     (vals execution-stats)))
        failures    (reduce + (map #(:failures % 0)    (vals execution-stats)))
        checks-ok   (reduce + (map #(:checks-ok % 0)   (vals execution-stats)))
        checks-fail (reduce + (map #(:checks-fail % 0) (vals execution-stats)))]
    {:tests  tests
     :success success
     :failures failures
     :successful-checks checks-ok
     :failed-checks checks-fail
     :success-rate (double (if (= tests 0) 0 (/ success tests)))}))



(defmethod reporter-factory :rdt/print-summary
  [_]
  (fn [execution-stats]
    (let [{:keys [tests success failures success-rate successful-checks failed-checks]} (brief-summary execution-stats)]
      (println
        (format
          (str
            "\n"
            "==== Test summary ====\n"
            "  Total tests: %,6d\n"
            "           OK: %,6d\n"
            "       Failed: %,6d\n"
            " Success rate:   %3.0f%%\n"
            "    Checks OK: %,6d\n"
            "Checks Failed: %,6d\n"
            "======================\n")
          tests success failures (* success-rate 100.0) successful-checks failed-checks)))))



(defn- print-failure
  [test check error]
  (println
    (format
      (str
        "==================================[ FAILURE ]===================================\n"
        "      TEST: %s\n"
        "  TEST LOC: %s\n\n"
        "EXPRESSION:\n%s\n\n"
        "     ERROR:\n%s\n\n"
        "================================================================================\n\n")
      (:name test)
      (:location test)
      (str/join "\n" (map ut/display (:form check)))
      (ut/indent-by "\t" (ut/pr-ex-str error)))))



(defmethod reporter-factory :rdt/print-failures
  [_]
  (fn [execution-stats]
    (->> execution-stats
      vals
      (filter #(and (number? (:failures %)) (pos? (:failures %))))
      (mapcat (fn [{:keys [errors test]}]
                (for [[exception meta] (filter second errors)]
                  [test meta exception])))
      (sort-by (juxt (comp :ns :test) (comp :location :test)))
      (run! (partial apply print-failure)))))
