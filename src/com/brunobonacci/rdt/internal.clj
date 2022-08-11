(ns com.brunobonacci.rdt.internal
  (:require [where.core :refer [where]]
            [com.brunobonacci.rdt.checkers :as chk]
            [clojure.string :as str]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                ----==| T E R M   R E W R I T I N G |==----                 ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn -separate-statements
  [body]
  (->>
    (concat body [:rdt/void])
    ;; separate statements
    (partition 3 1 '(:rdt/void :rdt/void :rdt/void))
    (reduce (fn [state [left test right :as triplet]]
         (cond
           (pos? (:drop state)) (update state :drop dec)
           (#{'=> '==>} test)   (-> state (update :forms conj (vec triplet)) (update :drop + 2))
           :else                (update state :forms conj [left])))
      {:forms [] :drop 0})
    ;; did I miss something?
    (#(if (pos? (:drop %))
        (throw (ex-info "Missing value after checking arrows" {:form (last (:forms %))}))
        %))
    ;; return statements
    :forms))



(comment
  (-separate-statements
    '(:ok
      (def foo 1)
      (def bar 2)

      (println foo)
      (println bar)

      (+ foo bar)  => 3

      (def foo 2)
      (println (+ foo bar))

      (assoc {} :foo (+ foo bar) :bar 22)
      => {:foo 4 }

      (println "end"))))



(defn -wrap-statement
  [last-sym meta forms ]
  `(if (second ~last-sym) ~last-sym (try [(do ~forms) nil ~meta] (catch Exception x# [nil x# ~meta]))))



(defn checker
  [[left test right :as triplet]]
  (cond
    (nil? test)
    nil

    (and (list? right) (= 'throws (first right)))
    ['throws `chk/throws-checker]

    (= test '=>)
    ['=> `chk/fuzzy-checker]

    (= test '==>)
    ['==> `chk/exact-checker]

    :else
    (throw
      (ex-info (format "Unknown or invalid checker %s" (pr-str test))
        {:form triplet}))))



(defn -statements->executable
  [statements]
  (let [_last (gensym "_last_")
        def?  (where [:and [list? :is? true] [first :is? 'def]])
        defn? (where [:and [list? :is? true] [first :is? 'defn]])]
    (->> statements
      (mapv (fn [[left test right :as triplet]]
              (let [checkable? (not (nil? test))
                    checker* (checker triplet)
                    meta {:form (list `quote (vec triplet))
                          :checkable? checkable?
                          :def?  (def? left)
                          :defn? (defn? left)
                          :checking-symbol (list `quote (first checker*))
                          :checking-funciton (second checker*)}]

                (cond
                  (def? left)
                  [[(second left) :as _last] (-wrap-statement _last meta (last left))]

                  (defn? left)
                  [[(second left) :as _last] (-wrap-statement _last meta (cons `fn (rest left)))]

                  (and checkable? (= 'throws (first checker*)))
                  [_last (-wrap-statement _last meta `(~(second checker*) ~(second right) (fn [] ~left)))]

                  checkable?
                  [_last (-wrap-statement _last meta `(~(second checker*) ~right (fn [] ~left)))]

                  :else ;; statement
                  [_last (-wrap-statement _last meta left)]))
              ))
      (#(conj % [_last `(if (second ~_last) (throw (second ~_last)) (first ~_last))]))
      (cons [_last nil]))))



(comment
  (->> '((+ 2 1)  => 3)
    (-separate-statements)
    (-statements->executable)
    )

  (->> '((/ 1 0)  => (throws Exception))
    (-separate-statements)
    (-statements->executable)
    )
  )



(defn -fact->checks
  {:no-doc true}
  [body final]
  (->> body
    (-separate-statements)
    (-statements->executable)
    ((fn [statements]
       `(let ~(vec (mapcat identity (drop-last 1 statements)))
          ;; finalizer
          (try ~@final (catch Exception fx#
                         ;; TODO: use mulog
                         (println "Failed to execute finalizer due to" (ex-message fx#))))
          ;; last statement value
          ~(->> statements (take-last 1) first second))))))



(defmacro fact->checks
  {:no-doc true}
  [body final]
  (-fact->checks body final))



(defmacro fact->checks2
  {:no-doc true}
  [& body]
  (-fact->checks body '()))



(comment


  (fact->checks2
    (defn foo [n] (* n 2))
    (foo 3) => 6)

  (fact->checks2
    (def foo 1))

  (fact->checks2
    (+ 1 2) => 3.0)

  (fact->checks2
    (+ 1 2) ==> 3.0)

  (fact->checks2
    :ok
    (def foo 1)
    (def bar 2)

    (println foo)
    (println bar)

    (+ foo bar)  => 3
    (/ foo 0)
    (def foo 2)
    (println (+ foo bar))

    (assoc {} :foo (+ foo bar) :bar 22)
    => {:foo 4 }

    (println "end")

    )


  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                      ----==| R E G I S T R Y |==----                       ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn sha256
  "hex encoded sha-256 hash"
  [^String data]
  (let [md        (java.security.MessageDigest/getInstance "SHA256")
        signature (.digest md (.getBytes data "utf-8"))
        size      (* 2 (.getDigestLength md))
        hex-sig   (.toString (BigInteger. 1 signature) 16)
        padding   (str/join (repeat (- size (count hex-sig)) "0"))]
    (str padding hex-sig)))



(def registry
  (atom {}))



(defn register-test
  [id meta testfn]
  (swap! registry assoc id {:id id :meta meta :fn testfn}))



(def ^:dynamic *runner* :inline)



(defn run-test
  [test-id]
  (when (= :inline *runner*)
    (when-let [test (get-in @registry [test-id :fn])]
      (test))))



(defn register-and-run
  [id meta testfn]
  (register-test id meta testfn)
  (run-test id))
