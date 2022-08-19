(ns com.brunobonacci.rdt.runner
  (:require [com.brunobonacci.rdt.internal :as i]
            [com.brunobonacci.rdt.checkers :as chk]
            [where.core :refer [where]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [com.brunobonacci.mulog.flakes :as f])
  (:gen-class))



(defn- classpath
  []
  (-> (System/getProperty "java.class.path")
    (str/split (re-pattern (System/getProperty "path.separator")))))



(defn lazy-list-dir
  [base]
  (tree-seq
    (memfn ^java.io.File isDirectory)
    (memfn ^java.io.File listFiles)
    (io/file base)))



(defn lazy-list-files
  [base]
  (->> (lazy-list-dir base)
    (filter (memfn ^java.io.File isFile))))



(defn relative-path
  [relative-to]
  (let [^String base (.getCanonicalPath ^java.io.File (io/file relative-to))]
    (fn [f]
      (let [^String f (.getCanonicalPath ^java.io.File (io/file f))]
        (if (str/starts-with? f base)
          (subs f (inc (count base)))
          f)))))



(defn lazy-list-relative-files
  [base]
  (let [relative (relative-path base)]
    (->> (lazy-list-files base)
      (map (fn [f] {:base base :relative (relative f) :absolute (.getAbsolutePath ^java.io.File f)})))))



(defn current-dir
  []
  (System/getProperty "user.dir"))



(defn project-dirs
  []
  (->> (classpath)
    (filter (fn [f] (.isDirectory (io/file f))))
    (distinct)
    (map (relative-path (current-dir)))))



(defn- file-to-ns
  [f]
  (-> (str f)
    (str/replace #"\.clj(s|c)?$" "")
    (str/replace #"/" ".")
    (str/replace #"_" "-")))



(defn find-tests-files
  [folders include-patterns exclude-patterns]
  (let [include-patterns (if (= include-patterns :all) [".*"] include-patterns)]
    (->> folders
      (filter (fn [f] (.isDirectory (io/file f))))
      (mapcat lazy-list-relative-files)
      (distinct)
      (filter (where [:or [:relative :ends-with? "_test.clj"] [:relative :ends-with? "_test.cljc"]]))
      (map (fn [{:keys [relative] :as m}] (assoc m :ns (file-to-ns relative))))
      (filter (fn [{:keys [ns]}] (some #(re-find (re-pattern %) ns) include-patterns)))
      (remove (fn [{:keys [ns]}] (some #(re-find (re-pattern %) ns) exclude-patterns))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                       ----==| R U N N E R S |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def stats (atom {}))



(def ^:dynamic *test-execution-id* nil)



(defn matches-labels?
  [include-labels exclude-labels]
  (fn [{:keys [labels]}]
    (let [labels (set labels)
          include-labels?  (if (= include-labels :all)
                             (constantly true)
                             #(not (empty? (set/intersection (set include-labels) %))))
          exclude-labels?  (if (empty? exclude-labels)
                             (constantly false)
                             #(not (empty? (set/intersection (set exclude-labels) %))))]

      (and (include-labels? labels)
        (not (exclude-labels? labels))))))



(defmethod i/runner :batch-runner
  [{:keys [include-labels exclude-labels]} test]
  (let [test-info (test :test-info)
        id        (:id test-info)
        matches?  (matches-labels? include-labels exclude-labels)]
    (when (matches? test-info)
      (swap! stats
            (fn [stats]
              (-> stats
                (assoc-in  [*test-execution-id* id :test]       test-info)
                (update-in [*test-execution-id* id :executions] (fnil inc 0)))))
      (i/no-fail
        (i/do-with-exception (test)
          ;; OK
          (swap! stats
            (fn [stats]
              (-> stats
                (update-in [*test-execution-id* id :success]    (fnil inc 0)))))
          ;; FAIL
          (swap! stats
            (fn [stats]
              (-> stats
                (update-in [*test-execution-id* id :errors]     (fnil conj []) [$error nil])
                (update-in [*test-execution-id* id :failures]   (fnil inc  0))))))))))


(defn- count-checks
  [check-meta expression]
  (let [line-ok   (if (:checkable? check-meta) :checks-ok :expressions-ok)
        line-fail (if (:checkable? check-meta) :checks-fail :expressions-fail)]
    (fn []
      (i/do-with-exception (expression)
        ;; ok
        (swap! stats
          (fn [stats]
            (update-in stats
              [*test-execution-id* (:id (:test check-meta)) line-ok]
              (fnil inc 0))))
        ;; fail
        (swap! stats
          (fn [stats]
            (-> stats
              (update-in
                [*test-execution-id* (:id (:test check-meta)) line-fail]
                (fnil inc 0))
              (update-in
                [*test-execution-id* (:id (:test check-meta)) :errors]
                (fnil conj []) [$error check-meta]))))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                      ----==| W R A P P E R S |==----                       ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def wrapper-factory nil)  ;; for repl development



(defmulti wrapper-factory (fn [{:keys [level name]}] [level name]))



(defmethod wrapper-factory [:test :rdt/print-test-name]
  [_]
  (fn [{:keys [name]} test]
    (fn []
      (println "\t- Checking: " name "...")
      (test))))



(defmethod wrapper-factory [:test :rdt/print-test-outcome]
  [_]
  (fn [{:keys [name]} test]
    (fn []
      (i/do-with-exception (test)
        (println "\t- Checking: " name "-> OK")
        (println "\t- Checking: " name "-> FAILED")))))



(defn compose-wrappers
  [meta target ws]
  (->> (reverse ws)
    (reduce (fn [t w] (w meta t))
      target)))



(defn- -compile-wrappers
  [level wrappers]
  (fn [meta target]
    (->> wrappers
      (map (fn [w]
             (if (map? w)
               (assoc w :level level)
               {:level :test :name w})))
      (map wrapper-factory)
      (compose-wrappers meta target))))



(defn compile-wrappers
  [{:keys [test-wrappers expression-wrappers finalizer-wrappers] :as runner}]
  (assoc runner
    :rdt/test-wrapper       (-compile-wrappers :test       test-wrappers)
    :rdt/expression-wrapper (-compile-wrappers :expression expression-wrappers)
    :rdt/finalizer-wrapper  (-compile-wrappers :finalizer  finalizer-wrappers)))



(defn brief-summary
  [execution-stats]
  (let [tests       (reduce + (map :executions         (vals execution-stats)))
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



(defn print-summary
  [{:keys [tests success failures success-rate successful-checks failed-checks]}]
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
      tests success failures (* success-rate 100.0) successful-checks failed-checks)))



(defn- print-failure
  [test check error]
  (println
    (format
      (str
        "==================================[ FAILURE ]===================================\n"
        "      TEST: %s\n"
        "  TEST LOC: %s\n\n"
        "EXPRESSION:\n%s\n\n"
        "     ERROR:\n\t%s\n\n"
        "================================================================================\n\n")
      (:name test)
      (:location test)
      (str/join "\n" (map chk/display (:form check)))
      (ex-message error))))



(defn print-failures
  [execution-stats]
  (->> execution-stats
    vals
    (filter #(and (number? (:failures %)) (pos? (:failures %))))
    (mapcat (fn [{:keys [errors test]}]
              (for [[exception meta] (filter second errors)]
                [test meta exception])))
    (sort-by (juxt (comp :ns :test) (comp :location :test)))
    (run! (partial apply print-failure))))




(defn- apply-defaults
  [runner-config]
  (-> (merge i/*runner* {:type :batch-runner} runner-config)
    (assoc :test-execution-id (f/snowflake))))




(defn run-tests
  [runner-config]
  (let [runner-config (apply-defaults runner-config)
        runner-config (compile-wrappers runner-config)
        {:keys [folders include-patterns exclude-patterns test-execution-id]} runner-config]

    (binding [i/*runner*          runner-config
              *test-execution-id* test-execution-id]
      (->> (find-tests-files folders include-patterns exclude-patterns)
        (run! (fn [{:keys [ns]}]
                (println "  (*) Testing:" ns)
                #_(load-file absolute)
                (remove-ns (symbol ns))
                (require (symbol ns) :reload))))
      ;; return execution summary
      (i/do-with (get @stats test-execution-id)
        (swap! stats dissoc test-execution-id)))))



(comment


  (project-dirs)

  (find-tests-files (project-dirs) :all nil)

  (find-tests-files ["test"] :all nil)

  (def execution-stats
    (run-tests {:folders (project-dirs)
                :include-patterns :all
                :exclude-labels [:container]}))

  (brief-summary execution-stats)

  (print-failures execution-stats)

  stats
  (def stats (atom {}))

  )




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                          ----==| M A I N |==----                           ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;
;; - TODO: add test wrappers
;; - TODO: add reporters
;; - TODO: add remote runner
;; - TODO: hierarchical tests
;; -
;;


(defn -main [& args]
  (let [config (merge {:folders (project-dirs)} (when (first args) (read-string (first args))))
        execution-stats (run-tests config)
        {:keys [failures] :as summary} (brief-summary execution-stats)]
    (print-summary summary)
    (print-failures execution-stats)
    (System/exit failures)))
