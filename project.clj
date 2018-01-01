(defproject hicada "0.1.2"
  :description "A hiccup compiler for clojurescript"
  :url "http://www.github.com/rauhs/hicada"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src" "env/dev/clj"]

  :profiles {:dev
              {:source-paths ["env/dev/clj"]
               :dependencies [[figwheel-sidecar "0.5.4-6" :exclusions [org.clojure/clojure]]
                              [com.cemerick/piggieback "0.2.2" :exclusions [org.clojure/clojure]]
                              [org.clojure/tools.nrepl "0.2.10"]]
               :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                              :init-ns hicada.dev
                              :welcome (println "TODO")}}}


  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.229" :classifier "aot" :scope "provided"]
                 [com.cognitect/transit-clj "0.8.300" :scope "provided"]
                 [figwheel-sidecar "0.5.10" :scope "provided"]]

  :repositories [["clojars" {:sign-releases false}]]
  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.13"]
            [lein-environ "1.0.0"]
            [lein-ring "0.9.1"]]
  
  ;; Global exclusions are applied across the board, as an alternative
  ;; to duplication for multiple dependencies with the same excluded libraries.
  :exclusions [org.clojure/tools.nrepl]

  :uberjar-name "hicada.jar"

  :jvm-opts ["-Xverify:none"]

  :clean-targets ^{:protect false} ["resources/public/js"]

  :cljsbuild
  {:builds
   {:dev {:source-paths ["src/cljs" "demo"]
          :figwheel true
          :compiler {:output-to     "resources/public/js/app.js"
                     :output-dir    "resources/public/js/out"
                     :asset-path   "js/out"
                     :optimizations :none
                     :pretty-print  true}}
    :prod_debug {:source-paths ["src/cljs"]
                 :compiler {:output-to     "resources/public/cljs/production_debug/app.js"
                            :output-dir    "resources/public/cljs/production_debug/out"
                            :asset-path   "js/out"
                            :output-wrapper false
                            :pseudo-names true
                            :optimizations :advanced
                            :pretty-print  true}}
    :demo {:source-paths ["src/cljs" "demo"]
                 :compiler {:output-to     "resources/public/cljs/demo/app.js"
                            :output-dir    "resources/public/cljs/demo/out"
                            :asset-path   "js/out"
                            :output-wrapper false
                            ;;:static-fns true
                            :pseudo-names false
                            :optimizations :simple
                            :pretty-print  false}}
    :prod {:source-paths ["src/cljs"]
           :compiler {:output-to     "resources/public/cljs/production/app.js"
                      :output-dir    "resources/public/cljs/production/out"
                      :asset-path   "js/out"
                      :output-wrapper false
                      :static-fns true ;; should be true by default
                      :optimizations :advanced
                      :pretty-print  false}}}}

   :jar-exclusions  [#"resources" #"demo" #"docs" #"env" #"public" #"test" #"main" #"\.swp" #"templates"]
   :uberjar {:hooks [leiningen.cljsbuild]
             ;;:hooks [leiningen.cljsbuild]
             ;;:env {:production true}
             :aot :all
             ;;:resource-paths [];; no resources
             :omit-source false
             :source-paths ["src/cljs"]})
