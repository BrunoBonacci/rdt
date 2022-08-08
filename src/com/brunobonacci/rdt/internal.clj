(ns com.brunobonacci.rdt.internal
  (:require [where.core :refer [where]]
            [com.brunobonacci.rdt.checkers :as chk]))



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




(def checkers-map
  {nil   nil
   '=>   `chk/fuzzy-checker
   '==>  `chk/exact-checker})



(defn -statements->executable
  [statements]
  (let [_last (gensym "_last_")
        def?  (where [:and [list? :is? true] [first :is? 'def]])
        defn? (where [:and [list? :is? true] [first :is? 'defn]])]
    (->> statements
      (mapv (fn [[left test right :as triplet]]
              (let [checkable? (not (nil? test))
                    meta {:form (list `quote (vec triplet))
                          :checkable? checkable?
                          :def?  (def? left)
                          :defn? (defn? left)
                          :checking-symbol (list `quote test)
                          :checking-funciton (checkers-map test)}]

                (cond
                  (def? left)
                  [[(second left) :as _last] (-wrap-statement _last meta (last left))]

                  (defn? left)
                  [[(second left) :as _last] (-wrap-statement _last meta (cons `fn (rest left)))]

                  checkable?
                  [_last (-wrap-statement _last meta `(~(checkers-map test) ~right (fn [] ~left)))]

                  :else ;; statement
                  [_last (-wrap-statement _last meta left)]))
              ))
      (#(conj % [_last `(if (second ~_last) (throw (second ~_last)) (first ~_last))]))
      (cons [_last nil]))))



(comment
  (->> '(
       (def foo 1)
       (def bar 2)
       (+ foo bar)  => 3)
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



(comment



  (def t
    (fact->checks
      (defn foo [n] (* n 2))
      (foo 3) => 6))

  (-> ((-> t first :fn) {}) first second (#(% 5)))

  (def t
    (fact->checks
      (def foo 1)))


  (fact->checks
    (+ 1 2) ==> 3)

  (def t
    (fact->checks
      (+ 1 2) ==> 3))

  ((-> t first :fn) {})


  (fact->checks
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
