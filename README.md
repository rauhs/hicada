# hicada
Hiccup compiler similar to sablono

See: https://medium.com/@rauh/a-new-hiccup-compiler-for-clojurescript-8a7b63dc5128

# Clojars

[![Clojars Project](http://clojars.org/hicada/latest-version.svg)](http://clojars.org/hicada)


# Usage

There is *no* macro for you to use. You **must** create your own
macro and call hicada from it:


```clj
(defmacro html
  [body]
  (hicada.compiler/compile body {:create-element 'js/React.createElement
                                 :transform-fn (comp)
                                 :array-children? false}
                                 {} &env))
```

## First argument: `content`

This is your hiccup that you got passed to your macro and should pass along.

## Second argument: `opts`:

A map with the following keys:

- `:array-children?`: See the blog post for details. For product build of React
  only or you'll enojoy a lot of warnings :)

- `:create-element`: A symbol to a CLJS/JS function that gets called with the
  precompiled `type config-js child-or-children`. Defaults to
  `'js/React.createElement`
   
- `:wrap-input?`: If inputs should be wrapped. Try without! You're responsible
  for requiring the CLJS namespace (create a `.cljs` file with the same name as
    your macro namespace and it will get required automatically.

- `:rewrite-for?`: Rewrites simple `(for [x xs] ...)` into efficient reduce
  pushing into a JS array.

- `:emit-fn`: gets called with `[type config-js child-or-children]`

React Native special options:

- `:no-string-tags?`: If set to `true`: Never output string tags (don't exits in react native)

- `:default-ns`: Any unprefixed component will get prefixed with this namespace
  symbol (eg. `'foo.bar.xyz`)


## Third argument `handlers`

See the `default-handlers` var in `hicada.compiler` for examples. By default
`:*` (React Fragments) and `:>` (React components) are defined.

## Last argument `env`

Your macro environment. Pass in `&env`.

# Usage with other React libraries

You can use hicada with:

- Reagent, it will happily accept precompiled hiccup
- Rum, simple overwrite the `sablono.compiler/compile-html` macro.
- Om

# Examples

There is some examples in the bottom of the `hicada.compiler` namespace. For
instance, if you often clone elements you can write a DSL to support it:


```clj
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

;; =>
(js/React.createElement
 "div"
 nil
 (js/React.createElement "span" (js* "{'key':~{}}" "foo") a b c)
 (js/React.cloneElement x (js* "{'key':~{}}" k) one two)
 (js/React.cloneElement x (js* "{'key':~{}}" k)))

```

# License

EPL, same as Clojure.
