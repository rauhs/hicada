# hicada
Hiccup compiler similar to sablono


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

Reason for this is explained in... (TODO)

