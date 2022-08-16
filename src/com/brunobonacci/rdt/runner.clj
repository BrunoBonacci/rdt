(ns com.brunobonacci.rdt.runner
  (:require [com.brunobonacci.rdt.internal :as i]
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
      (try
        (let [result (test)]
          (swap! stats
            (fn [stats]
              (-> stats
                (update-in [*test-execution-id* id :executions] (fnil inc 0))
                (update-in [*test-execution-id* id :success]    (fnil inc 0)))))
          result)
        (catch Exception x
          (swap! stats
            (fn [stats]
              (-> stats
                (update-in [*test-execution-id* id :executions] (fnil inc  0))
                (update-in [*test-execution-id* id :errors]     (fnil conj []) x)
                (update-in [*test-execution-id* id :failures]   (fnil inc  0))))))))))



(defn brief-summary
  [test-execution-id]
  (let [execution   (get @stats test-execution-id)
        tests       (reduce + (map :executions         (vals execution)))
        success     (reduce + (map #(:success % 0)     (vals execution)))
        failures    (reduce + (map #(:failures % 0)    (vals execution)))
        checks-ok   (reduce + (map #(:checks-ok % 0)   (vals execution)))
        checks-fail (reduce + (map #(:checks-fail % 0) (vals execution)))]
    {:test-execution-id test-execution-id
     :tests  tests
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



(defn- count-checks
  [meta expression]
  (if-not (:checkable? meta)
    expression
    (fn []
      (i/do-with-exception (expression)
        ;; ok
        (swap! stats
          (fn [stats]
            (update-in stats
              [*test-execution-id* (:id (:test meta)) :checks-ok]
              (fnil inc 0))))
        ;; fail
        (swap! stats
          (fn [stats]
            (update-in stats
              [*test-execution-id* (:id (:test meta)) :checks-fail]
              (fnil inc 0))))))))



(defn run-tests
  [{:keys [folders include-patterns exclude-patterns runner test-execution-id include-labels exclude-labels]
    :or {include-patterns :all exclude-patterns  nil
         include-labels   :all exclude-labels    nil
         runner :batch-runner  test-execution-id (f/snowflake)}}]
  (binding [i/*runner*          {:type runner :include-labels include-labels :exclude-labels exclude-labels
                                 :expression-wrapper count-checks
                                 }
            *test-execution-id* test-execution-id]
    (->> (find-tests-files folders include-patterns exclude-patterns)
      (run! (fn [{:keys [ns]}]
              (println "  (*) Testing:" ns)
              #_(load-file absolute)
              (remove-ns (symbol ns))
              (require (symbol ns) :reload))))
    ;; return brief summary
    (i/do-with (brief-summary test-execution-id)
      (swap! stats dissoc test-execution-id))))



(comment


  (project-dirs)

  (find-tests-files (project-dirs) :all nil)

  (find-tests-files ["test"] :all nil)

  (run-tests {:folders (project-dirs)
              :include-patterns ["io.redefine.query-engine.rewrite.*"] #_:all
              :exclude-labels [:container]})

  stats

  )


;;
;; - TODO: count checks
;; - TODO: add test wrappers
;; - TODO: add reporters
;; - TODO: add remote runner
;; - TODO: print failures
;; - TODO: hierarchical tests
;; -
;;


(defn -main [& args]
  (let [config (merge {:folders (project-dirs)} (when (first args) (read-string (first args))))]
    (let [{:keys [failures] :as summary} (run-tests config)]
      (print-summary summary)
      (System/exit failures))))
