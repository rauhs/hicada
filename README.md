# hicada
Hiccup compiler similar to sablono

See: https://medium.com/@rauh/a-new-hiccup-compiler-for-clojurescript-8a7b63dc5128

# Usage

There is *no* macro for you to use. You **must** create your own
macro and call hicada from it:


```clj
(defmacro html
  [body]
  (hicada/compile body {:create-element 'js/React.createElement
                        :transform-fn (comp)
                        :array-children? false}))
```

# Clojars

[![Clojars Project](http://clojars.org/hicada/latest-version.svg)](http://clojars.org/hicada)

