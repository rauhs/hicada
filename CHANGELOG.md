# MASTER
New option: `:child-config` enables to change processing as you go down levels

# 0.1.7
React Native: Also camelcase :style when passing a vector.
Eg:
[:Text {:style [{:border-bottom "10"} other-styles]}]
Now also camelCases the :border-bottom style key

# 0.1.6
Allow users to fine tune camelCase - kebab-case conversion.

# 0.1.5
New option: `:server-render?` doens't genrated any `js*`.

# 0.1.4
A `[a {:some-props y} ch0 ch1]` will now work and call `createElement` on the
type `a`. Previously this was assumed to be a collection of ReactNodes, but IFF
the second element in the vector is a map, the collection assumption doesn't
make sense. This is equivalent to `[:> a {:some-props y} ch0 ch1]` now.

# 0.1.3
BAD release

# 0.1.2
New option `:rewrite-for?` which rewrites simple `(for [x xs] hiccup...)` into
efficient `reduce` outputting a JS array. Defaults to `false`.

# 0.1.1
[:h1.b {:className "a"}] didn't merge properly Also `:class-name`.
Thanks @roman01la.

# 0.1
Initial

