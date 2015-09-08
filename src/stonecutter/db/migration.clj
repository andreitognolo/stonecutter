(ns stonecutter.db.migration
  (:require [monger.ragtime :as monger-ragtime]
            [monger.collection :as coll]
            [ragtime.core :as ragtime]
            [clojure.tools.logging :as log]
            [stonecutter.util.uuid :as uuid]
            [stonecutter.config :as config]))

(defn do-to-coll [db coll f]
  (let [records (coll/find-maps db coll)]
    (doseq [record records]
      (coll/update-by-id db coll (:_id record) (f record)))))

(defn add-user-id [record]
  (if (:uid record)
    record
    (assoc record :uid (uuid/uuid))))

(defn add-user-uids [db]
  (log/info "Running migration add-user-id")
  (do-to-coll db "users" add-user-id)
  (log/info "Finished running migration add-user-id"))

(defn add-user-role [record]
 (if (:role record)
   record
   (assoc record :role (:default config/roles))))

(defn add-user-roles [db]
  (log/info "Running migration add-user-roles")
  (do-to-coll db "users" add-user-role)
  (log/info "Finished migration add-user-roles"))

;; IMPORTANT DO *NOT* MODIFY THE EXISTING MIGRATION IDS IN THIS LIST
(def migrations
  [{:id "add-user-uid"   :up add-user-uids}
   {:id "add-user-role"  :up add-user-roles}])

(defn run-migrations
  ([db]
    (run-migrations db migrations))
  ([db migrations]
   (let [index (ragtime/into-index migrations)]
     (ragtime/migrate-all db index migrations))))