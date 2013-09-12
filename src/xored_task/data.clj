(ns xored-task.data)

(defn now [] (double (java.lang.System/currentTimeMillis)))

(def db-print-enabled true)

(defn dbprint [& more]
  (if db-print-enabled
    (let [tid (.getId (Thread/currentThread))] 
      (print (str "[" tid "] [" (now) "] " (apply str (interpose " " more)) \newline))
      (flush))))

(defrecord Task 
  [project-name
   name
   fn])

(defrecord Project
  [pref-time ; amount of time a worker won't look at other projects
   workers-n ; amount of workers with running tasks from this project
   task-fns]) ; {name => task-fn}

(def projects (ref (hash-map))) ; {project-name => (ref Project),...}
(def projects-by-times (ref (sorted-map))) ; {update-time => project-name,...}
(def times-by-projects (ref (hash-map))) ; {project-name => update-time,...}

(defn create-project [pref-time tasks-n-type tasks-n f]
  (let [tn (case tasks-n-type
             :random (inc (rand tasks-n))
             :fixed tasks-n)]
    (->Project pref-time
               0
               (into {} (map (fn [n] [(str "Task " n) (partial f n)])
                             (range tn))))))

(defn remove-project-from-timeline [project-name]
  (let [time (get @times-by-projects project-name)]
    (alter projects-by-times dissoc time))
  (alter times-by-projects dissoc project-name))

(defn- get-suitable-time-to-insert [desired-time] ; returns time that should be used with projects-by-times
  (let [next-dt (inc desired-time)]
    (if-let [last-record-for-dt (first (rsubseq @projects-by-times >= desired-time < next-dt))] ; faster than (last (subseq ...))
      (/ (+ next-dt (key last-record-for-dt)) 2)
      desired-time)))

(defn add-project-to-timeline [project-name]
  (let [time (get-suitable-time-to-insert (now))]
    (alter projects-by-times assoc time project-name)
    (alter times-by-projects assoc project-name time)))

(defn update-project-in-timeline [project-name]
  (let [old-time (get @times-by-projects project-name)]
    (alter projects-by-times dissoc old-time))
  (add-project-to-timeline project-name))


(defrecord Worker
  [name
   task ; Task, nil only at the beginning
   future]) ; nil

(def workers (ref (hash-map))) ; {worker-name => (ref Worker),...}

(defn create-worker [worker-name watch]
  (add-watch (ref (->Worker worker-name nil nil)) nil watch))

;(defn assign-task-to-worker [worker task]
;  (alter worker assoc :task task))

(defn worker-idle? [worker]
  (when (nil? (get @worker :future))
    worker))

(defn get-idle-worker []
  (some worker-idle? (vals (ensure workers)))) ; 'ensure' instead of '@' to avoid the use of removed worker


(defrecord Dispatcher
  [type ; :worker or :project
   name])

(def dispatcher (agent (->Dispatcher nil nil)))

(defn init-dispatcher [watch]
  (add-watch dispatcher nil watch))

(defn notify-about-idle-worker [worker-name]
  (send dispatcher assoc
        :type :worker
        :name worker-name))

(defn notify-about-free-task [project-name]
  (send dispatcher assoc
        :type :project
        :name project-name))


(defrecord Event
  [time
   type
   data])

(def events (agent [])) ; [Event,...]

(defn add-event [type data]
  (send events conj (->Event (now) type data)))


(defn reset-all-data []
  (dosync
    (doseq [[_ w] @workers]
      (if-let [f (get @w :future)]
        (future-cancel f)))
    (alter workers empty)
    (alter projects empty)
    (alter projects-by-times empty)
    (alter times-by-projects empty)
    (if (agent-error dispatcher)
      (restart-agent dispatcher (->Dispatcher nil nil) :clear-actions true)
      (send dispatcher assoc :type nil :name nil))
    (if (agent-error events)
      (restart-agent events [] :clear-actions true)
      (send events empty))))
