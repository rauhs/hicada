(ns hicada.normalize
  "
  Mostly from sablono + hiccup project.
  "
  (:require
    [hicada.util :as util]))

(defn compact-map
  "Removes all map entries where the value of the entry is empty."
  [m]
  (reduce
    (fn [m k]
      (let [v (get m k)]
        (if (empty? v)
          (dissoc m k) m)))
    m (keys m)))

(defn class-name
  [x]
  (cond
    (string? x) x
    (keyword? x) (name x)
    :else x))

(defn vec+stringify-class
  "Normalize `class` into a vector of classes (keywords will be stringified)."
  [klass]
  (cond
    (nil? klass)
    nil

    (list? klass)
    (if (symbol? (first klass))
      [klass]
      (map class-name klass))

    (symbol? klass)
    [klass]

    (string? klass)
    [klass]

    (keyword? klass)
    [(class-name klass)]

    (or (set? klass)
        (sequential? klass))
    (mapv class-name klass)

    (map? klass)
    [klass]

    :else klass))
#_(vec+stringify-class :foo)

(defn attributes
  "Normalize the :class elements"
  [attrs]
  (if (:class attrs)
    (update attrs :class vec+stringify-class)
    attrs))

(defn merge-with-class
  "Like clojure.core/merge but concatenate :class entries."
  [m0 m1]
  (let [m0 (attributes m0)
        m1 (attributes m1)
        classes (into [] (comp (mapcat :class)) [m0 m1])
        ;classes (vec (apply concat classes))
        ]
    (cond-> (conj m0 m1)
      (not (empty? classes))
      (assoc :class classes))))
#_(merge-with-class {})

(defn strip-css
  "Strip the # and . characters from the beginning of `s`."
  [s]
  (when (some? s)
    (cond
      (.startsWith s ".") (subs s 1)
      (.startsWith s "#") (subs s 1)
      :else s)))
#_(strip-css "#foo")
#_(strip-css ".foo")

(defn match-tag
  "Match `s` as a CSS tag and return a vector of tag name, CSS id and
  CSS classes."
  [s]
  (let [matches (re-seq #"[#.]?[^#.]+" (subs (str s) 1))
        [tag-name names]
        (cond (empty? matches)
              (throw (ex-info (str "Can't match CSS tag: " s) {:tag s}))
              (#{\# \.} (ffirst matches)) ;; shorthand for div
              ["div" matches]
              :default
              [(first matches) (rest matches)])]
    [(keyword tag-name)
     (first (map strip-css (filter #(= \# (first %1)) names)))
     (vec (map strip-css (filter #(= \. (first %1)) names)))]))
#_(match-tag :.foo.bar#some-id)
#_(match-tag :foo/span.foo.bar#some-id.hi)

(defn children
  "Normalize the children of a HTML element."
  [x]
  (->> (cond
         (string? x)
         (list x)

         (util/element? x)
         (list x)

         (and (list? x)
              (symbol? x))
         (list x)

         (list? x)
         x

         (and (sequential? x)
              (sequential? (first x))
              (not (string? (first x)))
              (not (util/element? (first x)))
              (= (count x) 1))
         (children (first x))

         (sequential? x)
         x
         :else (list x))
       (remove nil?)))

(defn element
  "Given:
  [:div.x.y#id (other)]
  Returns:
  [:div {:id \"id\"
         :class [\"x\" \"y\"]}
    (other)]"
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag)))
    (throw (ex-info (str tag " is not a valid element name.") {:tag tag :content content})))
  (let [[tag id klass] (match-tag tag)
        tag-attrs (compact-map {:id id :class klass})
        map-attrs (first content)]
    (if (map? map-attrs)
      [tag
       (merge-with-class tag-attrs map-attrs)
       (children (next content))]
      [tag
       (attributes tag-attrs)
       (children content)])))
#_(element [:div#foo 'a])
#_(element [:div.a#foo])

