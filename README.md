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

- `:array-children?`: See the blog post for details. For product build of React only or you'll enojoy a lot of warnings :)

- `:create-element`: A symbol to a CLJS/JS function that gets called with the precompiled `type config-js child-or-children`. Defaults to `'js/React.createElement`
   
- `:wrap-input?`: If inputs should be wrapped. Try without! You're responsible for requiring the CLJS namespace (create a `.cljs` file with the same name as your macro namespace and it will get required automatically.

- `:rewrite-for?`: Rewrites simple (for [x xs] ...) into efficient reduce pushing into
                          a JS array.

- `:emit-fn`: gets called with `[type config-js child-or-children]`

React Native special options:

- `:no-string-tags?`: If set to `true`: Never output string tags (don't exits in react native)

- `:default-ns`: Any unprefixed component will get prefixed with this namespace symbol (eg. `'foo.bar.xyz`)


## Third argument `handlers`

See the `default-handlers` var in `hicada.compiler` for examples. By default `:*` (React Fragments) and `:>` (React components) are defined.

## Last argument `env`

Your macro environment. Pass in `&env`.

# License

EPL, same as Clojure.
