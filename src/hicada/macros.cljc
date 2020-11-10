(ns hicada.macros)

(defmacro with-child-config [form expanded-form & body]
  `(let [cfg# hicada.compiler/*config*
         new-cfg# ((:child-config hicada.compiler/*config*) hicada.compiler/*config* ~form ~expanded-form)]
     (binding [hicada.compiler/*config* new-cfg#] ~@body)))
