(ns xored-task.util)

(defmacro sync-future [& body]
  `(let [p# (promise)]
     (with-meta
       (future-call (^{:once true} fn* [] (deref p#) ~@body))
       {:sync-future-latch p#})))

(defn sync-future-start [f]
  (deliver (:sync-future-latch (meta f)) nil)
  f)

(defn sync-future-wait? [f]
  (not (or (realized? (:sync-future-latch (meta f)))
           (future-cancelled? f))))
