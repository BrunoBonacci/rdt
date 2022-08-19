(ns com.brunobonacci.rdt.runner
  (:require [com.brunobonacci.rdt.internal  :as i]
            [com.brunobonacci.rdt.reporters :as rep]
            [com.brunobonacci.mulog.flakes  :as f]
            [where.core :refer [where]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :as set])
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
        matches?  (matches-labels? include-labels exclude-labels)]
    (when (matches? test-info)
      (i/no-fail
        (test)))))






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
        (println "\t- Checking: " name "-> FAILED" (ex-message $error))))))



(def stats (atom {}))



(defmethod wrapper-factory [:test :rdt/stats-count-tests]
  [_]
  (fn [{:keys [id] :as test-info} test]
    (swap! stats
      (fn [stats]
        (-> stats
          (assoc-in  [*test-execution-id* id :test]       test-info)
          (update-in [*test-execution-id* id :executions] (fnil inc 0)))))

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
            (update-in [*test-execution-id* id :failures]   (fnil inc  0))))))))



(defmethod wrapper-factory [:expression :rdt/stats-count-checks]
  [_]
  (fn [check-meta expression]
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
                  (fnil conj []) [$error check-meta])))))))))



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
               {:level level :name w})))
      (map wrapper-factory)
      (compose-wrappers meta target))))



(defn compile-wrappers
  [{:keys [test-wrappers expression-wrappers finalizer-wrappers] :as runner}]
  (assoc runner
    :rdt/test-wrapper       (-compile-wrappers :test       test-wrappers)
    :rdt/expression-wrapper (-compile-wrappers :expression expression-wrappers)
    :rdt/finalizer-wrapper  (-compile-wrappers :finalizer  finalizer-wrappers)))



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

  (rep/brief-summary execution-stats)

  stats
  (def stats (atom {}))

  )




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                          ----==| M A I N |==----                           ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;
;; - TODO: add remote runner
;; - TODO: hierarchical tests
;; - TODO: externalize defaults
;; - TODO: better cmd line handling


(defn -main [& args]
  (let [config (apply-defaults
                 (merge {:folders (project-dirs)}
                   (when (first args) (read-string (first args)))))
        execution-stats    (run-tests config)
        reporters          (rep/compile-reporters (:reporters config))
        {:keys [failures]} (rep/brief-summary execution-stats)]
    (reporters  execution-stats)
    (System/exit failures)))
