(ns claro.runtime.state
  (:require [claro.runtime.caching :as caching]))

;; ## Initialization

(defn initialize
  [opts value]
  (transient
    {:opts      opts
     :value     value
     :done?     false
     :cache     (caching/init-cache opts)
     :iteration 0
     :cost      0
     :batches   nil}))

;; ## Access

(defn opt
  [{:keys [opts]} k & [default]]
  (get opts k default))

(defn impl
  [state]
  (opt state :impl))

(defn value
  [{:keys [value]}]
  value)

(defn batches
  [{:keys [batches]}]
  batches)

(defn first-iteration?
  [{:keys [iteration]}]
  (zero? iteration))

(defn done?
  [{:keys [done?]}]
  done?)

(defn done
  [state]
  (assoc! state :done? true))

;; ## Batches

(defn assert-cost-limit
  [{:keys [opts cost] :as state}]
  (let [{:keys [max-cost check-cost?]
         :or {max-cost 256, check-cost? true}} opts]
    (when (and check-cost? (> cost max-cost))
      (throw
        (IllegalStateException.
          (format "resolution has exceeded maximum cost: %s > %s"
                  cost
                  max-cost)))))
  state)

(defn- update-cost
  [{:keys [opts cost batches] :as state}]
  (let [{:keys [cost-fn] :or {cost-fn count}} opts]
    (->> (reduce
           (fn [cost batch]
             (+ cost (cost-fn batch)))
           cost batches)
         (assoc! state :cost)
         (assert-cost-limit))))

(defn set-batches
  [state batches]
  (-> state
      (assoc! :batches batches)
      (update-cost)))

;; ## Cache

(defn from-cache
  [{:keys [opts cache]} resolvable not-found]
  (caching/read-cache opts cache resolvable not-found))

;; ## Finalization

(defn- update-cache
  [{:keys [opts cache] :as state} resolvable->value]
  (let [cache' (caching/update-cache opts cache resolvable->value)]
    (assoc! state :cache cache')))

(defn- update-iteration-count
  [{:keys [:iteration] :as state}]
  (assoc! state :iteration (inc iteration)))

(defn- update-value
  [state new-value]
  (assoc! state :value new-value))

(defn finalize
  [state resolvable->value value]
  (-> state
      (update-value value)
      (update-cache resolvable->value)
      (update-iteration-count)))
