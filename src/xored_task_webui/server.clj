(ns xored-task-webui.server
  (:use [compojure.core])
  (:require [compojure.route :as route]
            [noir.util.middleware :as nm]
            [noir.response :as nr]
            [xored-task.core :as xtcore]
            [xored-task-webui.pages :as pages]))

(def data-routes
  (context "/get-data" []
           (GET "/workers" []
                (nr/json (xtcore/get-workers)))
           (GET "/projects" []
                (nr/json (xtcore/get-projects)))
           (GET "/len" []
                (nr/json (xtcore/get-last-event-number)))
           (GET "/events" {{len :len} :params}
                (nr/json (when len (xtcore/get-events (Integer/parseInt len)))))))

(def action-routes
  (context "/action" []
           (GET "/add-projects" {{:keys [n pref-time tasks-n-type tasks-n code]} :params}
                (xtcore/add-projects (Integer/parseInt n)
                                     (Integer/parseInt pref-time)
                                     (keyword tasks-n-type)
                                     (Integer/parseInt tasks-n)
                                     code)
                (nr/empty))
           (GET "/add-workers" {{n :n} :params}
                (xtcore/add-workers (Integer/parseInt n))
                (nr/empty))
           (GET "/remove-workers" {{names :names} :params}
                (doseq [name names] (xtcore/remove-worker name))
                (nr/empty))
           (GET "/reset" []
                (xtcore/reset)
                (nr/redirect "/"))))

(defroutes app-routes
  (GET "/" [] (pages/index-page))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (nm/app-handler [data-routes
                   action-routes
                   app-routes]))

;(defn -main [& m]
;  (let [mode (keyword (or (first m) :dev))
;        port (Integer. (get (System/getenv) "PORT" "8080"))]
;    (server/start port {:mode mode
;                        :ns 'test-website})))
