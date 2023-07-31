(defn ver [] "0.5.0-alpha5")
(defn ts  [] (System/currentTimeMillis))
(defn jdk [] (clojure.string/replace (str (System/getProperty "java.vm.vendor") "-" (System/getProperty "java.vm.version")) #" " "_"))

(defproject com.brunobonacci/rdt #=(ver)
  :description "RDT - REPL-Driven tests"

  :url "https://github.com/BrunoBonacci/rdt"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/rdt.git"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.brunobonacci/where "0.5.6"]
                 [com.taoensso/nippy "3.2.0"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server" "-Djdk.attach.allowAttachSelf"]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]
                                  [com.clojure-goes-fast/clj-async-profiler "0.5.0"]
                                  [jmh-clojure "0.4.0"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-jmh "0.3.0"]]}}

  :main com.brunobonacci.rdt.runner

  :aliases
  {"perf-quick"
   ["with-profile" "dev" "jmh"
    #=(pr-str {:file "./dev/perf/benchmarks.edn"
               :status true :pprint true :format :table
               :fork 1 :measurement 5
               :output #=(clojure.string/join "-" ["./reservoir" #=(ver) #=(jdk) #=(ts) "results.edn"])})]

   "perf"
   ["with-profile" "dev" "jmh"
    #=(pr-str {:file "./dev/perf/benchmarks.edn"
               :status true :pprint true :format :table
               :output #=(clojure.string/join "-" ["./reservoir" #=(ver) #=(jdk) #=(ts) "results.edn"])})]

   "test"
   ["run"]

   "midje"
   ["run"]
   }
  )
