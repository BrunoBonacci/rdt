(ns com.brunobonacci.rdt.runner
  (:require [com.brunobonacci.rdt.internal :as i]
            [where.core :refer [where]]
            [clojure.string :as str]))



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



(comment


  (project-dirs)

  (find-tests-files (project-dirs) :all nil)

  (find-tests-files ["test"] :all nil)

  (binding [*runner* nil]
    (run! load-file (find-tests-files (project-dirs) :all nil)))

  (count @i/registry)
  )
