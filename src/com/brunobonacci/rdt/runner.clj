(ns com.brunobonacci.rdt.runner
  (:require [com.brunobonacci.rdt.internal  :as i]
            [com.brunobonacci.rdt.reporters :as rep]
            [com.brunobonacci.rdt.utils     :as ut]
            [where.core :refer [where]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.edn :as edn])
  (:gen-class))



(def ^:const DEFAULT-RUNNER
  {:type               :batch-runner

   :reporters
   [:rdt/print-summary
    :rdt/print-failures]

   :include-patterns   :all
   :exclude-patterns    nil

   :include-labels     :all
   :exclude-labels      nil

   ;; wrappers
   :test-wrappers
   [:rdt/stats-count-tests   ;; required for reporting
    ;;:rdt/print-test-name
    ;;:rdt/print-test-outcome
    ]

   :expression-wrappers
   [:rdt/stats-count-checks ;; required for reporting
    ]
   :finalizer-wrappers  []


   ;; internal - overridden by compile-wrappers
   :rdt/test-wrapper       (fn [test-info test] test)
   :rdt/expression-wrapper (fn [meta expression] expression)
   :rdt/finalizer-wrapper  (fn [test-info finalizer] finalizer)})



(def ^:dynamic *test-execution-id* nil)



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| U T I L S |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn find-tests-files
  [folders include-patterns exclude-patterns]
  (let [include-patterns (if (= include-patterns :all) [".*"] include-patterns)]
    (->> folders
      (filter (fn [f] (.isDirectory (io/file f))))
      (mapcat ut/lazy-list-relative-files)
      (distinct)
      (filter (where [:or [:relative :ends-with? "_test.clj"] [:relative :ends-with? "_test.cljc"]]))
      (map (fn [{:keys [relative] :as m}] (assoc m :ns (ut/file-to-ns relative))))
      (filter (fn [{:keys [ns]}] (some #(re-find (re-pattern %) ns) include-patterns)))
      (remove (fn [{:keys [ns]}] (some #(re-find (re-pattern %) ns) exclude-patterns))))))



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
      (ut/do-with-exception (test)
        (println "\t- Checking: " name "-> OK")
        (println "\t- Checking: " name "-> FAILED" #_(ex-message $error))))))



(def stats (atom {}))



(defmethod wrapper-factory [:test :rdt/stats-count-tests]
  [_]
  (fn [{:keys [id] :as test-info} test]
    (swap! stats
      (fn [stats]
        (-> stats
          (assoc-in  [*test-execution-id* id :test]       test-info)
          (update-in [*test-execution-id* id :executions] (fnil inc 0)))))

    (ut/do-with-exception (test)
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
        (ut/do-with-exception (expression)
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



(defmethod wrapper-factory [:test :rdt/send-test-count]
  [{:keys [client]}]
  (fn [{:keys [name]} test]
    (fn []
      (ut/do-with-exception (test)
        @(client :send [:rdt/running-stats :tests-ok 1])
        @(client :send [:rdt/running-stats :tests-fail 1])))))



(defmethod wrapper-factory [:expression :rdt/send-checks-count]
  [{:keys [client]}]
  (fn [check-meta expression]
    (fn []
      (ut/do-with-exception (expression)
        (when (:checkable? check-meta)
          @(client :send [:rdt/running-stats :checks-ok   1]))
        (when (:checkable? check-meta)
          @(client :send [:rdt/running-stats :checks-fail 1]))))))



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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                       ----==| R U N N E R S |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def test-runner nil)         ;; for repl dev


(defmulti test-runner :type)



(defmethod i/evaluator :batch-runner
  [{:keys [include-labels exclude-labels]} test]
  (let [test-info (test :test-info)
        matches?  (matches-labels? include-labels exclude-labels)]
    (when (matches? test-info)
      (ut/no-fail
        (test)))))



(defn- apply-defaults
  [runner-config]
  (-> (merge DEFAULT-RUNNER runner-config)
    (assoc :test-execution-id (ut/uuid))))



(defmethod test-runner :batch-runner
  [runner-config]
  (let [runner-config (apply-defaults runner-config)
        runner-config (compile-wrappers runner-config)
        {:keys [folders include-patterns exclude-patterns test-execution-id]} runner-config]

    (binding [i/*evaluator*          runner-config
              *test-execution-id* test-execution-id]
      (->> (find-tests-files folders include-patterns exclude-patterns)
        (run! (fn [{:keys [ns]}]
                (println "  (*) Testing:" ns)
                (remove-ns (symbol ns))
                (require (symbol ns) :reload))))
      ;; return execution summary
      (ut/do-with (get @stats test-execution-id)
        (swap! stats dissoc test-execution-id)))))



(defn child-process-cmd
  [runner-config]
  (let [runner-config (select-keys runner-config
                        [:reporters :include-patterns :exclude-patterns
                         :include-labels :exclude-labels :test-wrappers
                         :expression-wrappers :finalizer-wrappers :server])
        runner-config (assoc runner-config :type :batch-runner)
        java-cmd (ut/java-base-command)
        ;; add main
        java-cmd (conj java-cmd "com.brunobonacci.rdt.runner")
        ;; add args
        java-cmd (conj java-cmd (ut/pr-str (assoc runner-config :type :sub-runner)))]
    java-cmd))



(defmethod test-runner :parent-runner
  [runner-config]
  (let [stats  (atom {})
        server (ut/server-socket 0
                 (ut/clojure-data-wrapper
                   (fn [[r v x :as msg]]
                     (case r
                       :rdt/sub-process-ready
                       :ok

                       :rdt/execution-stats
                       (do (swap! stats assoc :rdt/execution-stats v) :ok)

                       :rdt/running-stats
                       (do (swap! stats update-in [:rdt/execution-stats v] (fnil + 0) x) :ok)
                       ;; else
                       (throw (ex-info "Unrecognized message" {:message msg}))))))

        ;; starting server
        cmd (child-process-cmd (assoc runner-config :server {:port (server :port)}))
        ;; _ (println cmd)

        _ (println "Initiating testing process...")
        ;; start thread to print live stats
        counter (ut/live-counter stats)
        child (ut/sh cmd)]
    ;; stop counter thread
    (counter)
    ;; stop server
    (server :close)

    ;;(println (-> child :out))
    (when-not (= 0 (-> child :exit))
      (println "Subprocess failed. Exit code:" (-> child :exit) )
      (println (-> child :out))
      (System/exit (-> child :exit)))
    (:rdt/execution-stats @stats)))



(defmethod test-runner :sub-runner
  [runner-config]
  (let [client (ut/client-socket "127.0.0.1" (-> runner-config :server :port) ut/serialize ut/deserialize)
        _ @(client :send [:rdt/sub-process-ready])
        runner-config (-> runner-config
                        (assoc :type :batch-runner)
                        (update :test-wrappers (fnil conj [])
                          {:name :rdt/send-test-count   :client client})
                        (update :expression-wrappers (fnil conj [])
                          {:name :rdt/send-checks-count :client client}))
        ;; run the tests
        execution-stats (test-runner runner-config)]
    ;; send execution stats to parent process
    @(client :send [:rdt/execution-stats execution-stats])
    ;; stop client
    (client :close)
    {}))



(defn run-tests
  [runner-config]
  (let [runner-config (apply-defaults runner-config)
        runner-config (compile-wrappers runner-config)]
    (test-runner runner-config)))



(comment

  (ut/project-dirs)

  (find-tests-files (ut/project-dirs) :all nil)

  (find-tests-files ["test"] :all nil)

  (def execution-stats
    (run-tests {:folders (ut/project-dirs)
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
;; - TODO: hierarchical tests
;; - TODO: externalize defaults
;; - TODO: better cmd line handling
;; - TODO: documentation
;; - TODO: markdown reporter
;; - TODO: send sub-process console to temp file as well.
;; - TODO: context info in the exception (like expression and so on)


(defn -main [& args]
  (try
    (let [config (apply-defaults
                   (merge {:folders (ut/project-dirs)}
                     (when (first args) (edn/read-string (first args)))))
          execution-stats    (run-tests config)
          reporters          (rep/compile-reporters (:reporters config))
          {:keys [failures]} (rep/brief-summary execution-stats)]
      (reporters  execution-stats)
      (System/exit failures))
    (catch Exception x
      (println x)
      (System/exit 1))))
