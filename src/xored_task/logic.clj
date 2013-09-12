(ns xored-task.logic
  (:use [xored-task.data]
        [xored-task.util]))

(defn- take-task-from-project [project-name]
  (let [project (get @projects project-name)
        tasks (get @project :task-fns)]
    (when-first [task-record tasks]
      (let [task-name (key task-record)]
        (alter project assoc
               :workers-n (inc (get @project :workers-n))
               :task-fns (dissoc tasks task-name))
        (->Task project-name task-name (val task-record))))))

(defn- check-time-and-take-task-from-project [pref-project-name]
  (if-let [project (get @projects pref-project-name)]
    (let [pref-time (get @project :pref-time)]
      (if-let [pp-time (get @times-by-projects pref-project-name)]
        (if (< (- (now) pp-time) pref-time)
          (take-task-from-project pref-project-name))))))

(defn- take-next-task [pref-project-name]
  (or (if pref-project-name (check-time-and-take-task-from-project pref-project-name))
      (loop [pbt @projects-by-times]
        (when-let [pbt-record (first pbt)]
          (or (take-task-from-project (val pbt-record))
              (recur (next pbt)))))))


(defn- finish-task [worker-name task]
  (let [p-name (get task :project-name)]
    (dosync
      (let [p-time (get @times-by-projects p-name)
            project (get @projects p-name)
            new-workers-n (dec (get @project :workers-n))]
        (if (and (zero? new-workers-n) (empty? (get @project :task-fns)))
          (do
            (alter projects dissoc p-name)
            (remove-project-from-timeline p-name)
            (add-event :project-finish {:name p-name}))
          (do
            (alter project assoc :workers-n new-workers-n)
            (update-project-in-timeline p-name)))))))

(defn- create-worker-future [worker task]
  (let [wn (get @worker :name)
        event-data {:worker-name wn
                    :project-name (get task :project-name)
                    :name (get task :name)}]
    (sync-future
      (add-event :task-start event-data)
      (try
        (dosync ; note that there is no need to extend this transaction to the whole future
          ((get task :fn))
          (alter worker assoc :future nil))
        (add-event :task-finish event-data)
        (finish-task wn task)
        (notify-about-idle-worker wn)
        (catch InterruptedException e
          (.interrupt (Thread/currentThread)))
        (catch Exception e
          (add-event :exception {:text (.getMessage e)})
          (notify-about-idle-worker wn)))
      "done")))

(defn worker-watch [_ wr old-v new-v]
  (when-let [wf (get new-v :future)]
    (when (sync-future-wait? wf)
      (sync-future-start wf)))) ; need to be started outside of transaction

(defn- assign-task-to-worker [worker task]
  (let [wf (create-worker-future worker task)]
    (dosync
      (if (contains? (ensure workers) (get @worker :name)) ; to be sure that the worker hasn't been removed
        (alter worker assoc :task task :future wf)
        (future-cancel wf)))))


(defmacro check-worker-and-assign-free-task [worker-expr]
  `(dosync
     (when-let [w# ~worker-expr]
       (when-let [t# (take-next-task (some-> @w# :task :project-name))]
         (assign-task-to-worker w# t#)))))


(defn dispatcher-watch [_ da old-v new-v]
  (case (get new-v :type)
    :worker (check-worker-and-assign-free-task (get (ensure workers) (get new-v :name)))
    :project (loop [continue true]
               (when continue
                 (recur (check-worker-and-assign-free-task (get-idle-worker)))))
    nil nil))
