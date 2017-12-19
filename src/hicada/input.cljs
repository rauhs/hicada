(ns hicada.input
  "
  DO NOT USE! This ns will be unmainted!


  Code for controlled input.
  Copied form sablono.
  "
  (:require
    [clojure.string :refer [blank? join]]
    [goog.object :as object]
    [goog.functions :as gf]))

(defn update-state
  "Updates the state of the wrapped input element."
  [component next-props property value]
  (let [on-change (object/getValueByKeys component "state" "onChange")
        next-state #js{}]
    (object/extend next-state next-props #js {:onChange on-change})
    (object/set next-state property value)
    (.setState component next-state)))

(defn wrap-form-element [element property]
  (let [ctor (fn [props]
               (this-as this
                 (set! (.-state this)
                       (let [state #js {}]
                         (->> #js{:onChange (goog/bind (object/get this "onChange") this)}
                              (object/extend state props))
                         state))
                 (.call js/React.Component this props)))]
    (set! (.-displayName ctor) (str "wrapped-" element))
    (goog/inherits ctor js/React.Component)
    (specify! (.-prototype ctor)
      Object
      (onChange [this event]
        (when-let [handler (.-onChange (.-props this))]
          (handler event)
          (update-state
            this (.-props this) property
            (object/getValueByKeys event "target" property))))

      (componentWillReceiveProps [this new-props]
        (let [state-value (object/getValueByKeys this "state" property)
              element-value (object/get (js/ReactDOM.findDOMNode this) property)]
          ;; On IE, onChange event might come after actual value of
          ;; an element have changed. We detect this and render
          ;; element as-is, hoping that next onChange will
          ;; eventually come and bring our modifications anyways.
          ;; Ignoring this causes skipped letters in controlled
          ;; components
          ;; https://github.com/facebook/react/issues/7027
          ;; https://github.com/reagent-project/reagent/issues/253
          ;; https://github.com/tonsky/rum/issues/86
          ;; TODO: Find a better solution, since this conflicts
          ;; with controlled/uncontrolled inputs.
          ;; https://github.com/r0man/sablono/issues/148
          (if (not= state-value element-value)
            (update-state this new-props property element-value)
            (update-state this new-props property (object/get new-props property)))))

      (render [this]
        (js/React.createElement element (.-state this))))
    ctor))

(def wrapped-input (gf/cacheReturnValue
                     #(wrap-form-element "input" "value")))
(def wrapped-checked (gf/cacheReturnValue
                       #(wrap-form-element "input" "checked")))
(def wrapped-select (gf/cacheReturnValue
                      #(wrap-form-element "select" "value")))
(def wrapped-textarea (gf/cacheReturnValue
                        #(wrap-form-element "textarea" "value")))

