(ns xored-task-webui.pages
  (:use [hiccup.page :only [html5 include-js include-css]]
        [hiccup.form :only [select-options]]))

(defn index-page []
  (html5
    [:head
     [:title "Timeline"]
     (include-js "http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js")
     (include-js "http://code.highcharts.com/stock/highstock.js")
     (include-js "http://code.highcharts.com/modules/exporting.js")
     (include-js "/js/watch.js")
     (include-js "/js/chart.js")
     (include-css "/css/main.css")]
    [:body
     [:div#container {:style "height: 300px"}]
     [:br]
     [:div
      [:div.input-section
       [:select#workers.list {:multiple ""} nil]
       [:br]
       [:input#workers-amount {:type "number" :value 1 :min 1 :style "width: 50px"}]
       [:button#add-workers "Add workers"]
       [:button#remove-workers "Remove selected workers"]]
      [:div.input-section
       [:select#projects.list {:multiple true} nil]
       [:br]
       [:span "Tasks in new project&nbsp;"]
       [:select#tasks-in-project-type nil
        (select-options [["Fixed" "fixed"]
                         ["Random up to" "random"]] 0)]
       [:input#tasks-in-project-amount {:type "number" :value 10 :min 1}]
       [:br]
       [:span "Preference period&nbsp;"]
       [:input#pref-time {:type "number" :value 3000 :min 0}]
       [:span "&nbsp;ms"]
       [:br]
       [:span "Task code preset&nbsp;"]
       [:select#task-type nil
        (select-options [["Fixed time" "fixed"]
                         ["Random time" "random"]] 0)]
       [:br]
       [:textarea#task-code ""]
       [:br]
       [:input#projects-amount {:type "number" :value 1 :min 1 :style "width: 50px"}]
       [:button#add-projects "Add projects"]]
      [:div.input-section
       [:select#completed-projects.long-list {:multiple true, :disabled true} nil]]
      [:div.input-section
       [:span "Update interval&nbsp;"]
       [:input#update-interval {:type "number" :value 500 :min 100 :style "width: 50px"}]
       [:span "&nbsp;ms"]
       [:br]
       [:br]
       [:br]
       [:p "Unstable - use only if en error occured"]
       [:form {:action "/action/reset"}
        [:button#reset "RESET"]]]]]))
