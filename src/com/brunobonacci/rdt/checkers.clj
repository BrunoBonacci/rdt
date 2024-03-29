(ns com.brunobonacci.rdt.checkers
  (:require [com.brunobonacci.rdt.utils :as ut]
            [where.core :refer [where]]
            [clojure.set :as set]))


(def primitive-arrays
  #{(Class/forName "[B")                     ;; bytes
    (Class/forName "[S")                     ;; shorts
    (Class/forName "[I")                     ;; ints
    (Class/forName "[J")                     ;; longs
    (Class/forName "[F")                     ;; floats
    (Class/forName "[D")                     ;; doubles
    (Class/forName "[C")                     ;; chars
    (Class/forName "[Z")                     ;; booleans
    (Class/forName "[Ljava.lang.Object;")    ;; objects
    })



(defn primitive-array?
  "Returns true if x is a primitive array."
  [x]
  (-> x type primitive-arrays boolean))



(defn- atomic-value?
  "Returns true if `value` is atomic, false otherwise."
  [value]
  (some #(% value) [nil? boolean? number? keyword? string? symbol? char?]))



(defn- zip-lists
  ([l1 l2]
   (zip-lists nil l1 l2))
  ([empty-value l1 l2]
   (let [l1 (concat l1 (repeat :rdt/<missing-value>))
         l2 (concat l2 (repeat :rdt/<missing-value>))]
     (->> (map vector l1 l2)
       (take-while (partial some #(not= :rdt/<missing-value> %)))
       (map (fn [e] (mapv #(if (= :rdt/<missing-value> %) empty-value %) e)))))))



(defn- fetch-keys
  [m keys]
  (into {}
    (for [k keys]
      [k (get m k :rdt/<missing-value>)])))



(defn- match-error
  [rpattern rvalue ppattern pvalue pattern value]
  (throw
    (ex-info
      (format "Unable to match pattern <%s> to value <%s>.\n\n\tExpected:\n%s\n\n\tActual:\n%s\n\n\n"
        (ut/pr-str pattern) (ut/pr-str value) (ut/display ppattern) (ut/display pvalue))
      {:error-type     ::match-failed
       :pattern        pattern
       :value          value
       :parent-pattern ppattern
       :parent-value   pvalue
       :root-pattern   rpattern
       :root-value     rvalue})))



(defn- regex?
  [pattern]
  (= java.util.regex.Pattern (type pattern)))




(defn- partial-matcher
  ([pattern value]
   (partial-matcher pattern value pattern value pattern value))
  ([rpattern rvalue ppattern pvalue pattern value]
   (cond
     (= pattern value)
     true

     (and (atomic-value? pattern) (atomic-value? value))
     (or (= pattern value)
       (and (number? pattern) (number? value) (== pattern value))
       (match-error rpattern rvalue ppattern pvalue pattern value))

     (and (sequential? pattern) (primitive-array? value))
     (partial-matcher rpattern rvalue ppattern pvalue pattern (into [] value))

     (and (primitive-array? pattern) (primitive-array? value) (= (type pattern) (type value)))
     (partial-matcher rpattern rvalue ppattern pvalue (into [] pattern) (into [] value))

     (and (sequential? pattern) (sequential? value))
     (->> (zip-lists :rdt/<missing-value> pattern value)
       #_(filter (where first not= :rdt/<missing-value>))
       (map (partial apply partial-matcher rpattern rvalue pattern value))
       (every? true?))

     (and (set? pattern) (set? value))
     (or (set/subset? pattern value)
       (match-error rpattern rvalue ppattern pvalue pattern value))

     (and (map? pattern) (map? value))
     (->> pattern
       keys
       (fetch-keys value)
       (map (partial partial-matcher rpattern rvalue pattern value) pattern)
       (every? true?))

     (and (fn? pattern) (not (fn? value)))
     (or (pattern value)
       (match-error rpattern rvalue ppattern pvalue pattern value))

     (and (regex? pattern) (string? value))
     (or (boolean (re-matches pattern value))
       (match-error rpattern rvalue ppattern pvalue pattern value))

     (and (regex? pattern) (regex? value))
     (or (= (.pattern ^java.util.regex.Pattern pattern) (.pattern ^java.util.regex.Pattern value))
       (match-error rpattern rvalue ppattern pvalue pattern value))

     :else
     (match-error rpattern rvalue ppattern pvalue pattern value))))



(defn fuzzy-checker
  "A checker for the right-hand-side of the arrow which only checks the keys and items
   in the pattern and accepts additional keys without failing.
   See README.md for more info."
  [expected actual*]
  (partial-matcher expected (actual*)))



(defn exact-checker
  [expected actual*]
  (let [actual (actual*)]
    (or (= expected actual)
      (throw (ex-info (format "\n\n\tExpected:\n%s\n\n\tActual:\n%s\n\n\n"
                        (ut/display expected) (ut/display actual))
               {:expected expected :actual actual})))))




(defn throws-checker
  [expected actual*]
  (let [[actualv actuale] (try [(actual*)] (catch Throwable t [nil t]))]
    (or (instance? expected actuale)
      (throw (ex-info (format "\n\n\tExpected:\n%s\n\n\tActual:\n%s\n\n\n"
                        (ut/display expected) (ut/display (or (type actuale) actualv)))
               {:expected expected :actual (or actuale actualv)})))))
