(ns xored-task.core)
(use 'clojure.repl)


(def db-print-enabled true)

(defn dbprint [& more]
  (if db-print-enabled
    (let [tid (.getId (Thread/currentThread))] 
      (print (str "[" tid "] " (apply str (interpose " " more)) \newline))
      (flush))))


(defrecord Task 
  [project-name
   name
   fn])


(defrecord Project
  [task-fns]) ; {name => task-fn}


(defrecord Worker
  [name
   task ; Task
   future]) ; to be able to cancel it


(defrecord Dispatcher
  [worker ; worker-name
   project]) ; project-name


(defrecord Event
  [time
   type
   data])


(def projects (ref (hash-map))) ; {project-name => Project,...}
(def projects-by-times (ref (sorted-map))) ; {update-time => #{project-name, project-name,...},...}
(def times-by-projects (ref (hash-map))) ; {project-name => update-time,...}
(def workers (ref (hash-map))) ; {worker-name => (ref Worker),...}
(def dispatcher (agent (Dispatcher. nil nil)))
(def time-in-project (ref 10000)) ; 10 seconds by default
(def events (agent [])) ; [Event,...]


(defn now [] (java.lang.System/currentTimeMillis))


(defn update-project-time [project-name]
  (dbprint "update-project-time" project-name)
  (dosync
    (let [ct (now)
          ot ((ensure times-by-projects) project-name)
          old-record ((ensure projects-by-times) ot)
          tmp-map (assoc @projects-by-times ct (if (contains? @projects-by-times ct)
                                                (conj (projects-by-times ct) project-name)
                                                (hash-set project-name)))]
      (dbprint "ct" ct)
      (dbprint "ot" ot)
      (dbprint "old-record" old-record)
      (dbprint "tmp-map" tmp-map)
      (ref-set projects-by-times
               (cond
                 (> (count old-record) 1) (assoc tmp-map ot (dissoc old-record project-name))
                 ot (dissoc tmp-map ot)
                 true tmp-map))
      (alter times-by-projects assoc project-name ct)))
  (dbprint "/update-project-time"))


(defn set-task-done [task]
  (dbprint "set-task-done" task)
  (dosync
    (let [p-name (:project-name task)
          project ((ensure projects) p-name)
          new-tasks (dissoc (:task-fns project) (:name task))]
      (if (empty? new-tasks)
        (do (dbprint "new-tasks is empty")
          (let [p-time ((ensure times-by-projects) p-name)
                ps-w-same-time (disj ((ensure projects-by-times) p-time) p-name)]
            (dbprint "project-time" p-time)
            (alter projects dissoc p-name)
            (alter times-by-projects dissoc p-name)
            (if (empty? ps-w-same-time)
              (alter projects-by-times dissoc p-time)
              (alter projects-by-times assoc p-time ps-w-same-time))))
        (do
          (alter projects assoc p-name (assoc project :task-fns new-tasks))
          (update-project-time p-name)))))
  (dbprint "/set-task-done" task))


(defn- worker-idle? [worker]
  (if-let [f (:future @worker)]
    (if (realized? f) worker nil)
    worker))


(defn- get-idle-worker []
  (dbprint "get-idle-worker")
  (dosync
    (let [ret (some worker-idle? (vals (ensure workers)))]
      (dbprint "/get-idle-worker" (vals @ret))
      ret)))


(defn- worker-assign-and-do [worker task]
  (dbprint "worker-do-task" (:name @worker))
  (let [tid (.getId (Thread/currentThread))
        wn (:name @worker)
        pn (:project-name task)
        tn (:name task)
        wf (future
             (print (str "[" tid "] ### START: " wn " " tn \newline)) (flush)
             (send events conj (Event. (now) :task-start {:worker-name wn :project-name pn :task-name tn}))
             ((:fn task))
             (send events conj (Event. (now) :task-finish {:worker-name wn :project-name pn :task-name tn}))
             (print (str "[" tid "] ### FINISH: " wn " " tn \newline)) (flush)
             (set-task-done task)
             (send dispatcher assoc :worker wn)
             nil)]
    (dosync
      (alter worker assoc :task task :future wf)))
  (dbprint "/worker-do-task" (:name @worker))
  nil)


(defn task-unassigned? [task]
  (not-any? #(= (some-> @% :task :fn) task) (vals @workers)))


(defn get-task-from-project [project-name]
  (dbprint "get-task-from-project" project-name)
  (let [ret (some #(if (task-unassigned? (val %))
                     (Task. project-name (key %) (val %))
                     nil)
                  (:task-fns (projects project-name)))]
    (dbprint "/get-task-from-project" project-name ret)
    ret))


(defn get-projects-sorted-by-time [projects-by-time]
  (flatten (reduce #(vector %1 (vec %2)) [] (vals projects-by-time))))


(defn get-next-task [pref-project-name]
  (dbprint "get-next-task" pref-project-name)
  (dosync
    (let [pp-time ((ensure times-by-projects) pref-project-name)
          move-pp (and pp-time (< (- (now) pp-time) @time-in-project))
          alt-pbt (if move-pp
                    (assoc (dissoc (ensure projects-by-times) pp-time) 0 pref-project-name)
                    (ensure projects-by-times))
          project-queue (get-projects-sorted-by-time alt-pbt)]
      (dbprint "pp-time" pp-time)
      (dbprint "project-queue" project-queue)
      (let [ret (some get-task-from-project project-queue)]
        (dbprint "/get-next-task" pref-project-name ret)
        ret))))


(defn add-project [name project]
  (dbprint "add-project" name project)
  (let [ct (now)]
    (dosync
      (if (contains? (ensure projects) name)
        (throw (IllegalArgumentException. "The project with such name already exists")))
      (alter projects assoc name project)
      (alter projects-by-times assoc ct (conj (set (projects-by-times ct)) name))
      (alter times-by-projects assoc name ct)
      (send dispatcher assoc :project name)))
  (dbprint "/add-project" name)
  project)


(defn- dispatcher-watch [_ da old-v new-v]
  (dbprint "dispatcher-watch" (vals old-v) (vals new-v))
  (dosync
    (when-let [worker (get-idle-worker)]
      (dbprint "worker" (vals @worker))
      (when-let [task (get-next-task (some-> @worker :task :project-name))]
        (dbprint "task" (vals task))
        (worker-assign-and-do worker task))))
  (dbprint "/dispatcher-watch"))

(add-watch dispatcher nil dispatcher-watch)


(defn add-worker [name]
  (dbprint "add-worker" name)
  (let [worker (ref (Worker. name nil nil))]
    (dosync
      (alter workers assoc name worker)
      (send dispatcher assoc :worker name))
  (dbprint "/add-worker" name)))


(defn create-test-project [name size]
  (let [tid (.getId (Thread/currentThread))
        test-task (fn [task-name] (java.lang.Thread/sleep 100))]
    (Project. (apply hash-map (mapcat (fn [n]
                                        (let [task-name (str "Task " n)]
                                          [task-name (partial test-task task-name)]))
                                      (range size))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn test-x[]
  (add-worker "w0")
  (add-worker "w1")
  (add-project "pr" (create-test-project "pr" 5))
  nil)

(defn -main []
  (println "main"))
