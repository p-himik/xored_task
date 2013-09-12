(defproject xored_task "0.1.0-SNAPSHOT"
  :description "Solution for task distribution"
  :url "http://p-himik.ru/"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [lib-noir "0.6.8"]
                 [ring-server "0.3.0"]]
  :plugins [[lein-ring "0.8.7"]]
  :ring {:handler xored-task-webui.server/app
         :port 8080
         :auto-reload? true
         :stacktraces? true
         :nrepl {:start? true, :port 55555}})
