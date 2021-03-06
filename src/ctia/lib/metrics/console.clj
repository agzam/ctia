(ns ctia.lib.metrics.console
  (:require [clj-momo.lib.metrics.console :as console]
            [ctia.properties :as p]))

(defn init! []
  (let [{:keys [enabled interval]}
        (p/get-in-global-properties [:ctia :metrics :console])]
    (when enabled
      (console/start interval))))
