(ns xored-task.public
  (:use [xored-task.data]
        [xored-task.logic]))

(defn add-project [name project]
  (dosync
    (if (contains? (ensure projects) name)
      (throw (IllegalArgumentException. "The project with such name already exists")))
    (alter projects assoc name (ref project))
    (add-project-to-timeline name)
    (notify-about-free-task name)
    (add-event :project-add {:name name}))
  project)


(defn add-worker [name]
  (let [worker (create-worker name worker-watch)]
    (dosync
      (if (contains? @workers name)
        (throw (IllegalArgumentException. "The worker with such name already exists")))
      (alter workers assoc name worker)
      (notify-about-idle-worker name)
      (add-event :worker-add {:name name}))
    worker))


(defn remove-worker [name]
  (dosync
    (let [worker (get @workers name)]
      (if (nil? worker)
        (throw (IllegalArgumentException. "There is no such worker")))
      (alter workers dissoc name)
      (when-let [task (get @worker :task)]
        (when-let [f (get @worker :future)] ; otherwise the task is considered to be completed
          (future-cancel f)
          (let [p-name (get task :project-name)
                project (get @projects p-name)]
            (alter project assoc
                   :workers-n (dec (get @project :workers-n))
                   :task-fns (assoc (get @project :task-fns) (get task :name) (get task :fn)))
            (notify-about-free-task p-name))))))
  (add-event :worker-remove {:name name}))


(init-dispatcher dispatcher-watch)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-workers [] (map key @workers))
(defn get-projects [] (map key @projects))
(defn get-last-event-number [] (count @events))
(defn get-events [from-event-n] (nthrest @events from-event-n))

(def ^:private last-project-number (atom 0))

(defn add-projects [n pref-time tasks-n-type tasks-n code]
  (try
    (let [f (eval (read-string (str "(fn [n] " code ")")))
          to (swap! last-project-number + n)
          from (- to n)]
      (doseq [project-n (range from to)]
        (add-project (str "Project " project-n)
                     (create-project pref-time tasks-n-type tasks-n f))))
    (catch Exception e
      (add-event :exception {:text (.getMessage e)}))))

(def ^:private last-worker-number (atom 0))

(defn add-workers [n]
  (let [to (swap! last-worker-number + n)
        from (- to n)]
    (doseq [worker-n (range from to)]
      (add-worker (str "Worker " worker-n)))))

(defn reset []
  (dosync
    (reset-all-data)
    (reset! last-project-number 0)
    (reset! last-worker-number 0)))
