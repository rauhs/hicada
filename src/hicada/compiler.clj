(ns hicada.compiler
  "
  Hicada - Hiccup compiler aus dem Allgaeu

  NOTE: The code for has been forked like this:
  weavejester/hiccup -> r0man/sablono -> Hicada.

  Note about :array-children? :
  Go read the React.createElement() function, it's very short and easy to understand.
  Do you see how the children are just copied from the arguments and then just put into
  props.children? This is exactly what :array-children? avoids. It's completely safe to do.

  Dev Note: Do NOT use any laziness here! Not when generating code! Or it won't pick up
  the ^:dynamic config anymore!"
  (:refer-clojure :exclude [compile])
  (:require
    [hicada.normalize :as norm]
    [hicada.util :as util]))

(def default-handlers {:> (fn [_ klass attrs & children]
                            [klass attrs children])
                       :* (fn [_ attrs & children]
                            (if (map? attrs)
                              ['js/React.Fragment attrs children]
                              ['js/React.Fragment {} (cons attrs children)]))})

;; TODO: We should take &env around everything and also expect it as an argument.
(def default-config {:inline? false
                     :wrap-input? false
                     :array-children? false
                     :emit-fn nil
                     :rewrite-for? false
                     :server-render? false
                     ;; If you also want to camelcase string map keys, add string? here:
                     :camelcase-key-pred (some-fn keyword? symbol?)
                     ;; A fn that will get [tag attr children] and return
                     ;; [tag attr children] just before emitting.
                     :transform-fn identity
                     :create-element 'js/React.createElement})

(def ^:dynamic *config* default-config)
(def ^:dynamic *handlers* default-handlers)
(def ^:dynamic *env* nil)

(defmulti compile-react
          "Compile a Clojure data structure into a React fn call."
          (fn [x]
            (cond
              (vector? x) :vector
              (seq? x) :seq
              :else (class x))))

(defmulti compile-config-kv (fn [name value] name))

(defmethod compile-config-kv :class [name value]
  (cond (or (nil? value)
            (keyword? value)
            (string? value))
        value

        (and (or (sequential? value)
                 (set? value))
             (every? string? value))
        (util/join-classes value)

        (and (vector? value) (not (:server-render? *config*)))
        (apply util/join-classes-js value)

        :else value))

(defmethod compile-config-kv :style [name value]
  (util/camel-case-keys value))

(defmethod compile-config-kv :default [name value]
  value)

(defn compile-config
  "Compile a HTML attribute map to react (class -> className), camelCases :style."
  [attrs]
  (if (map? attrs)
    (reduce-kv (fn [m k v]
                 (assoc m
                   (case k
                     :class :className
                     :for :htmlFor
                     (if ((:camelcase-key-pred *config*) k)
                       (util/camel-case k)
                       k))
                   (compile-config-kv k v))) {} attrs)
    attrs))
#_(compile-config {:class ["b" 'c] :style {:border-width "2px"}}) ;; camelcase style
;; React native style:
#_(compile-config {:class ["b" 'c] :style [{:border-width "2px"}]}) ;; camelcase style
#_(compile-config {:on-click ()})


(defn- unevaluated?
  "True if the expression has not been evaluated.
   - expr is a symbol? OR
   - it's something like (foo bar)"
  [expr]
  (or (symbol? expr)
      (and (seq? expr)
           (not= (first expr) `quote))))
#_(unevaluated? '(foo))

(defn- form-name
  "Get the name of the supplied form."
  [form]
  (when (and (seq? form) (symbol? (first form)))
    (name (first form))))

(declare compile-html)

(defmulti compile-form
          "Pre-compile certain standard forms, where possible."
          form-name)

(declare emitter)
(defmethod compile-form "do"
  [[_ & forms]]
  `(do ~@(butlast forms) ~(emitter (last forms))))

(defmethod compile-form "array"
  [[_ & forms]]
  `(cljs.core/array ~@(mapv emitter forms)))

(defmethod compile-form "let"
  [[_ bindings & body]]
  `(let ~bindings ~@(butlast body) ~(emitter (last body))))

(defmethod compile-form "let*"
  [[_ bindings & body]]
  `(let* ~bindings ~@(butlast body) ~(emitter (last body))))

(defmethod compile-form "letfn*"
  [[_ bindings & body]]
  `(letfn* ~bindings ~@(butlast body) ~(emitter (last body))))

(defmethod compile-form "for"
  [[_ bindings body]]
  ;; Special optimization: For a simple (for [x xs] ...) we rewrite the for
  ;; to a fast reduce outputting a JS array:
  (if (:rewrite-for? *config*)
    (if (== 2 (count bindings))
      (let [[item coll] bindings]
        `(reduce (fn ~'hicada-for-reducer [out-arr# ~item]
                   (.push out-arr# ~(emitter body))
                   out-arr#)
                 (cljs.core/array) ~coll))
      ;; Still optimize a little by giving React an array:
      (list 'cljs.core/into-array `(for ~bindings ~(emitter body))))
    `(for ~bindings ~(emitter body))))

(defmethod compile-form "if"
  [[_ condition & body]]
  `(if ~condition ~@(doall (for [x body] (emitter x)))))

(defmethod compile-form "when"
  [[_ bindings & body]]
  `(when ~bindings ~@(doall (for [x body] (emitter x)))))

(defmethod compile-form "when-some"
  [[_ bindings & body]]
  `(when-some ~bindings ~@(butlast body) ~(emitter (last body))))

(defmethod compile-form "when-not"

  [[_ bindings & body]]
  `(when-not ~bindings ~@(doall (for [x body] (emitter x)))))

(defmethod compile-form "if-not"
  [[_ bindings & body]]
  `(if-not ~bindings ~@(doall (for [x body] (emitter x)))))

(defmethod compile-form "if-some"
  [[_ bindings & body]]
  `(if-some ~bindings ~@(doall (for [x body] (emitter x)))))

(defmethod compile-form "case"
  [[_ v & cases]]
  `(case ~v
     ~@(doall (mapcat
                (fn [[test hiccup]]
                  (if hiccup
                    [test (emitter hiccup)]
                    [(emitter test)]))
                (partition-all 2 cases)))))

(defmethod compile-form "condp"
  [[_ f v & cases]]
  `(condp ~f ~v
     ~@(doall (mapcat
                (fn [[test hiccup]]
                  (if hiccup
                    [test (emitter hiccup)]
                    [(emitter test)]))
                (partition-all 2 cases)))))

(defmethod compile-form "cond"
  [[_ & clauses]]
  `(cond ~@(mapcat
             (fn [[check expr]] [check (emitter expr)])
             (partition 2 clauses))))


(defmethod compile-form :default [expr] expr)

(defn- literal?
  "True if x is a literal value that can be rendered as-is."
  [x]
  (and (not (unevaluated? x))
       (or (not (or (vector? x) (map? x)))
           (and (every? literal? x)
                (not (keyword? (first x)))))))
#_(literal? [:div "foo"])

(declare emit-react)

(defn compile-react-element
  "Render an element vector as a HTML element."
  [element]
  (let [[tag attrs content] (norm/element element)]
    (emit-react tag attrs (when content (compile-react content)))))

(defn compile-element
  "Returns an unevaluated form that will render the supplied vector as a HTML element."
  [[tag attrs & children :as element]]
  (cond
    ;; Special syntax:
    ;; [:> ReactNav {:key "xyz", :foo "bar} ch0 ch1]
    (get *handlers* tag)
    (let [f (get *handlers* tag)
          [klass attrs children] (apply f element)]
      (emit-react klass attrs (mapv compile-html children)))

    ;; e.g. [:span "foo"]
    ;(every? literal? element)
    ;(compile-react-element element)

    ;; e.g. [:span {} x]
    (and (literal? tag) (map? attrs))
    (let [[tag attrs _] (norm/element [tag attrs])]
      (emit-react tag attrs (mapv compile-html children)))

    (literal? tag)
    ;; We could now interpet this as either:
    ;; 1. First argument is the attributes (in #js{} provided by the user) OR:
    ;; 2. First argument is the first child element.
    ;; We assume #2. Always!
    (compile-element (list* tag {} attrs children))

    ;; Problem: [a b c] could be interpreted as:
    ;; 1. The coll of ReactNodes [a b c] OR
    ;; 2. a is a React Element, b are the props and c is the first child
    ;; We default to 1) (handled below) BUT, if b is a map, we know this must be 2)
    ;; since a map doesn't make any sense as a ReactNode.
    ;; [foo {...} ch0 ch1] NEVER makes sense to interpret as a sequence
    (and (vector? element) (map? attrs))
    (emit-react tag attrs children)

    (seq? element)
    (seq (mapv compile-html element))

    ;; We have nested children
    ;; [[:div "foo"] [:span "foo"]]
    :else
    (mapv compile-html element)))
#_(compile-element '[:> A {:foo "bar"} a])
#_(compile-element '[:> A a b])
#_(compile-element '[A {:foo "bar"}
                     [:span  a]])
#_(compile-element '[A b a])
#_(compile-element '[:* 0 1 2])
#_(compile-element '(array [:div "foo"] [:span "foo"]))

(defn compile-html
  "Pre-compile data structures"
  [content]
  (cond
    (vector? content) (compile-element content)
    (literal? content) content
    :else (compile-form content)))

(defmethod compile-react :vector [xs]
  (if (util/element? xs)
    (compile-react-element xs)
    (compile-react (seq xs))))

(defmethod compile-react :seq [xs]
  (mapv compile-react xs))

(defmethod compile-react :default [x] x)

#_(ns-unmap *ns* 'to-js)
(defmulti to-js
          "Compiles to JS"
          (fn [x]
            (cond
              (:server-render? *config*) :server-render ;; ends up in default but let user handle it
              (map? x) :map
              (vector? x) :vector
              (keyword? x) :keyword
              :else (class x))))

(defn- to-js-map
  "Convert a map into a JavaScript object."
  [m]
  (when-not (empty? m)
    (let [key-strs (mapv to-js (keys m))
          non-str (remove string? key-strs)
          _ (assert (empty? non-str)
                    (str "Hicada: Props can't be dynamic:"
                         (pr-str non-str) "in: " (pr-str m)))
          kvs-str (->> (mapv #(-> (str \' % "':~{}")) key-strs)
                       (interpose ",")
                       (apply str))]
      (vary-meta
        (list* 'js* (str "{" kvs-str "}") (mapv to-js (vals m)))
        assoc :tag 'object)))
  ;; We avoid cljs.core/js-obj here since it introduces a let and an IIFE:
  #_(apply list 'cljs.core/js-obj
           (doall (interleave (mapv to-js (keys m))
                              (mapv to-js (vals m))))))

(defmethod to-js :keyword [x] (name x))
(defmethod to-js :map [m] (to-js-map m))
(defmethod to-js :vector [xs]
  (apply list 'cljs.core/array (mapv to-js xs)))
(defmethod to-js :default [x] x)

(defn collapse-one
  "We can collapse children to a non-vector if there is only one."
  [xs]
  (cond-> xs
    (== 1 (count xs)) first))

(defn tag->el
  "A :div is translated to \"div\" and symbol 'ReactRouter stays."
  [x]
  (assert (or (symbol? x) (keyword? x) (string? x) (seq? x))
          (str "Got: " (class x)))
  (if (keyword? x)
    (if (:no-string-tags? *config*)
      (symbol (or (namespace x) (some-> (:default-ns *config*) name)) (name x))
      (name x))
    x))


(defn emit-react
  "Emits the final react js code"
  [tag attrs children]
  (let [{:keys [transform-fn emit-fn inline? wrap-input?
                create-element array-children?]} *config*
        [tag attrs children] (transform-fn [tag attrs children *env*])]
    (if inline?
      (let [type (or (and wrap-input? (util/controlled-input-class tag attrs))
                     (tag->el tag))
            props (to-js
                    (merge (when-not (empty? children) {:children (collapse-one children)})
                           (compile-config (dissoc attrs :key :ref))))]
        (if emit-fn
          (emit-fn type (:key attrs) (:ref attrs) props)
          (list create-element type (:key attrs) (:ref attrs) props)))
      (let [children (if (and array-children?
                              (not (empty? children))
                              (< 1 (count children)))
                       ;; In production:
                       ;; React.createElement will just copy all arguments into
                       ;; the children array. We can avoid this by just passing
                       ;; one argument and make it the array already. Faster.
                       ;; Though, in debug builds of react this will warn about "no keys".
                       [(apply list 'cljs.core/array children)]
                       children)
            el (if-some [wrapper-class (util/controlled-input-class tag attrs)]
                 (if wrap-input?
                   wrapper-class
                   (tag->el tag))
                 (tag->el tag))
            cfg (to-js (compile-config attrs))]
        (if emit-fn
          (emit-fn el cfg children)
          (apply list create-element el cfg children))))))

(defn emitter
  [content]
  (cond-> (compile-html content)
    (:inline? *config*) to-js))

(defn compile
  "Arguments:
  - content: The hiccup to compile
  - opts
   o :array-children? - for product build of React only or you'll enojoy a lot of warnings :)
   o :create-element 'js/React.createElement - you can also use your own function here.
   o :wrap-input? - if inputs should be wrapped. Try without!
   o :rewrite-for? - rewrites simple (for [x xs] ...) into efficient reduce pushing into
                          a JS array.
   o :emit-fn - optinal: called with [type config-js child-or-children]
   o :server-render? - defaults to false. Doesn't do any JS outputting. Still requires an :emit-fn!
   o :camelcase-key-pred - defaults to (some-fn keyword? symbol?), ie. map keys that have
                           string keys, are NOT by default converted from kebab-case to camelCase!
   o :inline? false - NOT supported yet. Possibly in the future...

   React Native special recommended options:
   o :no-string-tags? - Never output string tags (don't exits in RN)
   o :default-ns - Any unprefixed component will get prefixed with this ns.
  - handlers:
   A map to handle special tags. See default-handlers in this namespace.
  - env: The macro environment. Not used currently."
  ([content]
   (compile content default-config))
  ([content opts]
   (compile content opts default-handlers))
  ([content opts handlers]
   (compile content opts handlers nil))
  ([content opts handlers env]
   (assert (not (:inline? opts)) ":inline? isn't supported yet")
   (binding [*config* (merge default-config opts)
             *handlers* (merge default-handlers handlers)
             *env* env]
     (emitter content))))

(comment

  (compile [:h1.b.c {:class "a"}]) ;; should be "b c a", order preserved
  (compile [:h1.b.c {:className "a"}])
  (compile [:h1.b.c {:class-name "a"}])

  (compile '[:div {:class [a]} "hmm"]
           {:server-render? true
            :emit-fn (fn [a b c]
                       (into [a b] c))})

  (compile '[:div (for [x xs]
                    [:span x])]
           {:rewrite-for? true})

  ;; Example :clone handler + emitter:
  (compile '[:div
             [:span {:key "foo"} a b c]
             [:clone x {:key k} one two]
             [:clone x {:key k}]]
           {:array-children? false ;; works with both!
            :emit-fn (fn [tag attr children]
                       ;; Now handle the emitter case:
                       (if (and (seq? tag) (= ::clone (first tag)))
                         (list* 'js/React.cloneElement (second tag) attr children)
                         (list* 'js/React.createElement tag attr children)))}
           {:clone (fn [_ node attrs & children]
                     ;; Ensure props + children are in the right position:
                     [(list ::clone node) attrs children])})

  (compile '[:* {:key "a"} a b])

  (compile '[:* a b])
  (compile '[:> :div props b])

  ;; Doesn't convert string keys, but do convert keywords & symbols:
  (compile '[X {"kebab-case" y :camel-case x camel-case-2 8}])

  (compile '[Transition {:in in-prop} (fn [state])]) ;; works eq to :>
  (compile '[a b c]) ;; We have a coll of ReactNodes. Don't touch
  (compile '(some-fn {:in in-prop} (fn [state]))) ;; FN call, don't touch

  (compile
    '[:> Transition {:in in-prop
                     :unmount-on-exit true
                     :timeout {:enter 300, :exit 100}}
      (fn [state])])

  ;; Issue #2:
  (compile '[:div {:ihtml "<div>hi</div>"}]
           {:transform-fn (fn [[tag attr ch]]
                            (if-some [html (:ihtml attr)]
                              [tag
                               (-> attr
                                   (dissoc :ihtml)
                                   (assoc :dangerouslySetInnerHTML {:__html html}))
                               ch]
                              [tag attr ch]))})

  (compile '[:Text a b]
           {:no-string-tags? true
            :default-ns 'my.rn.native})
  (compile '[:rn/Text a b] {})
  (compile '[:Text a b] {:no-string-tags? true})

  (compile '[:Text {:style [{:border-bottom "2px"}]}])

  (compile '[:div a b] {:array-children? false}))

