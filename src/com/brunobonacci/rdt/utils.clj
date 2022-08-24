(ns com.brunobonacci.rdt.utils
  (:refer-clojure :exclude [pr-str])
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [taoensso.nippy :as nippy])
  (:import [java.io PrintWriter OutputStream
            BufferedReader InputStreamReader
            InputStream Closeable]
           [java.net Socket ServerSocket]))


(defn uuid
  []
  (str (java.util.UUID/randomUUID)))


(defn java-executable []
  (str (System/getProperty "java.home")
    (System/getProperty "file.separator")
    "bin"
    (System/getProperty "file.separator")
    "java"))



(defn java-process-arguments
  []
  (into [] (.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean))))



(defn java-classpath
  []
  (System/getProperty "java.class.path"))



(defn- classpath-elements
  []
  (-> (java-classpath)
    (str/split (re-pattern (System/getProperty "path.separator")))))



(defn java-base-command
  "Returns the java command used to start the current JVM with
  all the JVM parameters and classpath up to, but excluding,
  the main class and its parameters"
  []
  (let [java-cmd [(java-executable)]
        java-cmd (apply conj java-cmd (java-process-arguments))
        java-cmd (apply conj java-cmd ["-cp" (java-classpath)])]
    java-cmd))



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
  (->> (classpath-elements)
    (filter (fn [f] (.isDirectory (io/file f))))
    (distinct)
    (map (relative-path (current-dir)))))



(defn file-to-ns
  [f]
  (-> (str f)
    (str/replace #"\.clj(s|c)?$" "")
    (str/replace #"/" ".")
    (str/replace #"_" "-")))



(defn pr-str
  "like clojure.core/pr-str but ignores `*print-level*` and `*print-length*`"
  [v]
  (binding [*print-length* nil
            *print-level*  nil]
    (clojure.core/pr-str v)))



(defn ppr-str
  "pretty print to string"
  [v]
  (binding [*print-length* nil
            *print-level*  nil]
    ;; pretty-printed representation
    (with-out-str
      (pp/pprint v))))



(defn indent-by
  [indent s]
  (-> s
    (str/replace #"\n" (str "\n" indent))
    ((partial str indent))))



(defn display
  ([v]
   (display "\t  " v))
  ([indent v]
   (indent-by indent (ppr-str v))))





(defmacro no-fail
  [& body]
  `(try ~@body (catch Exception x#)))



(defmacro do-with
  [value & forms]
  `(let [~'$it ~value]
     (no-fail ~@forms)
     ~'$it))



(defmacro do-with-exception
  [value ok fail]
  `(let [[~'$it ~'$error] (try [(do ~value) nil] (catch Exception x# [nil x#]))]
     (if ~'$error
       (do (no-fail ~fail) (throw ~'$error))
       (do (no-fail ~ok)   ~'$it))))



(defn sha256
  "hex encoded sha-256 hash"
  [^String data]
  (let [md        (java.security.MessageDigest/getInstance "SHA-256")
        signature (.digest md (.getBytes data "utf-8"))
        size      (* 2 (.getDigestLength md))
        hex-sig   (.toString (BigInteger. 1 signature) 16)
        padding   (str/join (repeat (- size (count hex-sig)) "0"))]
    (str padding hex-sig)))



(defmacro thunk
  [& body]
  `(fn [] ~@body))




;; Copyright © Samsara's authors.
;; Lifted/adpted from:
;; https://github.com/samsara/samsara/blob/master/moebius/src/moebius/core.clj
(defn thread-continuation
  "Execute the function `f` in a separate thread called `name`,
   and it return a function without arguments which when called it
  stop the execution of the thread.  The function `f` must accept one
  argument, its state and return the next state. The first state is provided
  at the start. If the thread completes its work it can return
  :thread-continuation/done, and the thread will be stopped.

  Between two execution the thread can optionally sleep for a configurable
  amount of time (in millis) with `:sleep-time 5000` option
  Ex:
      (def t (thread-continuation \"hello\"
               (fn [_] (println \"hello world\")) nil
               :sleep-time 3000))
      ;; in background you should see every 3s appear the following message
      ;; hello world
      ;; hello world
      ;; hello world
      ;; to stop the thread
      (t)
      (def t (thread-continuation \"counter\"
               (fn [counter]
                  (println \"counter:\" c)
                  ;; return the next value
                  (inc c))
                0   ;; initial state
               :sleep-time 1000))
      ;; in background you should see every 1s appear the following message
      ;; counter: 0
      ;; counter: 1
      ;; counter: 2
      ;; counter: 3
      ;; to stop the thread
      (t)
  "
  [^String name f state
   & {:keys [sleep-time] :or {sleep-time 0}}]
  (let [stopped (promise)
        last-state (promise)
        thread
        (Thread.
          ^Runnable
          (fn []
            (loop [state state]

              (let [state' (or (no-fail (f state)) state)]

                (when (> sleep-time 0)
                  (no-fail (Thread/sleep sleep-time)))


                ;; if the thread is interrupted then exit
                (if (or (realized? stopped) (= state' :thread-continuation/done))
                  (deliver last-state state')
                  (recur state')))))
          name)]
    (.start thread)
    ;; return a function without params which
    ;; when executed stop the thread
    (fn []
      (deliver stopped true)
      (.interrupt thread)
      @last-state)))



(defn server-socket
  "Trivial server-socket for client/server communication.
   It listen for messages on the given port. Multiple clients can connect
   at the same time, each client will be handled in a separate thread.
   The communication protocol is very simple, each message is sent on a single line
   so if you have new-lines characters in your message they need to be escaped as `\n`.
   The server handle the message with the give function and sends the response back
   to the client in a single line. If the handling of the function throws as exception
   the server will send a message to the client with the following format:

   ```
   !ERR: <server error message>
   ```

   If you specify `0` as the port, the server will choose a free random port.

   Here is how to use it. In this example we are starting a server on a random port
   and it will echo back any message sent but in upper-case letters:
   ```
  (def server (server-socket 0 str/upper-case))

  ;; return the server port
  (server :port) => 61835
  ;; return the list of open connections
  (server :open-connections)


  ;; start a client
  (def client (client-socket \"127.0.0.1\" (server :port) identity identity))
  @(client :send \"hello\") ;; => \"HELLO\"

  ;; shutdown client and servers
  (client :close)
  (server :close)
   ```
  "
  [port handler]

  (let [ ;; starting the server
        server (ServerSocket. port)
        ;; keep track of open connections
        open-connections (atom {})

        ;; some utility functions
        escape   (fn [msg] (str/replace (str msg) "\n" "\\n"))
        unescape (fn [msg] (when msg (str/replace msg "\\n" "\n")))
        handler  (fn [r] (try (handler r) (catch Exception x (str "!ERR: " (ex-message x)))))

        ;; initialize a client connection
        init-connection
        (fn [^Socket client]
          (let [out (PrintWriter. ^OutputStream (.getOutputStream client))
                in  (BufferedReader. (InputStreamReader. ^InputStream (.getInputStream client)))
                conn {:in in :out out :client client}]
            (swap! open-connections assoc client conn)
            conn))

        ;; send a message to a client
        send (fn [{:keys [out] :as connection} msg]
               (.println ^PrintWriter out (escape msg))
               (.flush ^PrintWriter out))

        ;; close connection
        close (fn [{:keys [in out client] :as connection}]
                (swap! open-connections dissoc client)
                (no-fail (.close ^Closeable in))
                (no-fail (.close ^Closeable out)))

        ;; starting server handling
        thread
        (thread-continuation "socket-server"
          (fn [server]
            (try
              (let [^Socket client (.accept ^ServerSocket server)]

                (no-fail
                  (let [connection (init-connection client)]

                    ;; Client connection handling
                    (thread-continuation "socket-server-handler"
                      (fn [{:keys [in out] :as connection}]
                        (try
                          (let [msg (unescape (.readLine ^BufferedReader in))]
                            (cond
                              (nil? msg) (do (close connection) :thread-continuation/done)
                              :else      (do (->> msg handler (send connection)) connection)))
                          (catch Exception _
                            (close connection)
                            :thread-continuation/done)))
                      connection)))

                server)
              (catch Exception _
                :thread-continuation/done)))
          server)]
    ;; function to inspect and stop the server.
    (fn this-server
      ([]
       (this-server :stop))
      ([cmd]
       (case cmd
         :close   (no-fail (.close ^Closeable server)
                    (run! (fn [[_ conn]] (no-fail (close conn))) @open-connections)
                    (thread)
                    :done)
         :server server
         :port  (.getLocalPort ^ServerSocket server)
         :open-connections @open-connections
         :unrecognized)))))



(defn client-socket
  "The client side communication function for the server-socket. see
  `server-socket` for more info.
  The send-wrapper is a function which takes a message and returns another message
  before it gets sent. The receive-wrapper is the same but for incoming messages."
  [host port send-wrapper receive-wrapper]
  ;; connecting the client
  (let [client (Socket. ^String host ^int port)
        ;; some utility functions
        escape   (fn [msg] (str/replace (str msg) "\n" "\\n"))
        unescape (fn [msg] (when msg (str/replace msg "\\n" "\n")))

        ;; initialize a client connection
        init-connection
        (fn [^Socket client]
          (let [out (PrintWriter. ^OutputStream (.getOutputStream client))
                in  (BufferedReader. (InputStreamReader. ^InputStream (.getInputStream client)))
                conn {:in in :out out :client client}]
            conn))
        ;; send a message to a client
        send (fn [{:keys [out] :as connection} msg]
               (.println ^PrintWriter out (escape msg))
               (.flush ^PrintWriter out))

        ;; send-receive
        send-receive
        (fn [{:keys [in out] :as connection} message]
          (send connection (send-wrapper message))
          (future
            (try
              (let [msg (unescape (.readLine ^BufferedReader in))]
                (if (str/starts-with? (or msg "") "!ERR:")
                  (ex-info (str "SERVER ERROR:" (subs msg 5))
                    {:type :server-error :request message :response msg})
                  (receive-wrapper msg)))
              (catch Exception x
                (ex-info (str "CLIENT ERROR:" (ex-message x))
                  {:type :celint-error :request message} x)))))

        ;; close connection
        close (fn [{:keys [in out client] :as connection}]
                (no-fail (.close ^Closeable in))
                (no-fail (.close ^Closeable out)))

        ;; init connection
        connection  (init-connection client)]
    (fn this-client
      ([] (this-client :close))
      ([cmd & args]
       (case cmd
         :send  (send-receive connection (first args))
         :close (close connection))))))



(comment

  (def server (server-socket 0 str/upper-case))

  (server :port)

  (server :open-connections)


  (def client (client-socket "127.0.0.1" (server :port) identity identity))

  (def resp (client :send "Hello"))

  @resp

  (client :close)
  (server :close)
  )



(defn serialize
  [data]
  (nippy/freeze-to-string data {:compressor nippy/lz4hc-compressor}))



(defn deserialize
  [data]
  (nippy/thaw-from-string data {:compressor nippy/lz4hc-compressor}))



(defn clojure-data-wrapper
  [handler]
  (fn [r]
    (serialize (handler (deserialize r)))))



(comment
  (def handler (->> #(update % :foo (fnil inc 0))
                 (clojure-data-wrapper)))


  (def server (server-socket 10000 handler))

  (server :port)

  (server :open-connections)


  (def client (client-socket "127.0.0.1" (server :port) serialize deserialize))

  (def resp (client :send {:foo 32}))

  @resp

  (client :close)
  (server :close)
  )



(defn live-counter
  [stats-atom]
  (let [rot ["-" "\\" "|" "/"]
        red   (str \u001b "[31m")
        green (str \u001b "[32m")
        reset (str \u001b "[0m")
        _ (flush)
        stop
        (thread-continuation "live-counter"
          (fn [p] (let [stats @stats-atom
                       test-ok     (get-in stats [:rdt/execution-stats :tests-ok] 0)
                       test-fail   (get-in stats [:rdt/execution-stats :tests-fail] 0)
                       checks-ok   (get-in stats [:rdt/execution-stats :checks-ok] 0)
                       checks-fail (get-in stats [:rdt/execution-stats :checks-fail] 0)]
                   (printf "\r%s [%s] Running %,d tests and %,d checks with %s failures so far...%s\r"
                     (if (pos? checks-fail) red green)
                     (get rot (mod p (count rot)))
                     (+ test-ok test-fail) (+ checks-ok checks-fail)
                     (if (pos? checks-fail) (format "%,d" checks-fail) "no")
                     reset)
                   (flush)
                   (inc p)))
          0 :sleep-time 125)]
    (fn [] (stop) (println) (flush))))




;; adapted from clojure core
(defn sh
  "Like `clojure.java/sh` but `err` and `out` are merged in the same stream
   and returned as string like in a terminal.
  `cmd` is an array of strings argument starting from the process name"
  [cmd]
  (let [in nil in-enc "UTF-8" out-enc "UTF-8" dir nil
        ^Process proc (.exec (Runtime/getRuntime)
                        ^"[Ljava.lang.String;" (into-array String cmd)
                        ^"[Ljava.lang.String;"(into-array String [])
                        ^java.io.File (io/as-file dir))]
    (if in
      (future
        (with-open [os (.getOutputStream proc)]
          (io/copy in os :encoding in-enc)))
      (.close (.getOutputStream proc)))
    (with-open [stdout (.getInputStream proc)
                stderr (.getErrorStream proc)]
      (let [bout (java.io.StringWriter.)
            out (future (io/copy stdout bout :encoding out-enc))
            err (future (io/copy stderr bout :encoding out-enc))
            exit-code (.waitFor proc)]
        @out @err
        (.close bout)
        {:exit exit-code :out (str bout) }))))
