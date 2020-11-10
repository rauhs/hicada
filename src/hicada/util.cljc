(ns hicada.util
  (:require
    [clojure.string :as str]
    [clojure.set :as set]))

(defn join-classes-js
  "Joins strings space separated"
  ([] "")
  ([& xs]
   (let [strs (->> (repeat (count xs) "~{}")
                   (interpose ",")
                   (apply str))]
     (list* 'js* (str "[" strs "].join(' ')") xs))))

(defn camel-case
  "Returns camel case version of the key, e.g. :http-equiv becomes :httpEquiv."
  [k]
  (if (or (keyword? k)
          (string? k)
          (symbol? k))
    (let [[first-word & words] (str/split (name k) #"-")]
      (if (or (empty? words)
              (= "aria" first-word)
              (= "data" first-word))
        k
        (-> (map str/capitalize words)
            (conj first-word)
            str/join
            keyword)))
    k))

(defn camel-case-keys
  "Recursively transforms all map keys into camel case."
  [m]
  (cond
    (map? m)
    (reduce-kv
      (fn [m k v]
        (assoc m (camel-case k) v))
      {} m)
    ;; React native accepts :style [{:foo-bar ..} other-styles] so camcase those keys:
    (vector? m)
    (mapv camel-case-keys m)
    :else
    m))

(defn element?
  "- is x a vector?
  AND
   - first element is a keyword?"
  [x]
  (and (vector? x) (keyword? (first x))))

(defn join-classes
  "Join the `classes` with a whitespace."
  [classes]
  (->> (map #(if (string? %) % (seq %)) classes)
       (flatten)
       (remove nil?)
       (str/join " ")))

(defn controlled-input-class
  "Returns the React class that is to be used for this component or nil if it's not a controlled
   input."
  [type attrs]
  (when (keyword? type)
    (case (name type)
      "input" (cond
                (:checked attrs) '(hiccada.input/wrapped-checked)
                (:value attrs) '(hiccada.input/wrapped-input)
                :else nil)
      "select" (when (:value attrs) '(hiccada.input/wrapped-select))
      "textarea" (when (:value attrs) '(hiccada.input/wrapped-textarea))
      nil)))
