(ns stonecutter.validation
  (:require [clojure.string :as s]))

(def name-max-length 70)

(def email-max-length 254)

(def password-min-length 8)

(def password-max-length 254)

(defn remove-nil-values [m]
  (->> m
       (remove (comp nil? second))
       (into {})))

(defn is-email-valid? [email]
  (when email
    (re-matches #"\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]+\b" email)))

(defn is-too-long? [string max-length]
  (> (count string) max-length))

(defn is-too-short? [string min-length]
  (< (count string) min-length))

(defn validate-registration-name [name]
  (cond (s/blank? name) :blank
        (is-too-long? name name-max-length) :too-long
        :default nil))

(defn validate-registration-email [email user-exists?-fn]
  (cond (s/blank? email) :blank
        (is-too-long? email email-max-length) :too-long
        (not (is-email-valid? email)) :invalid
        (user-exists?-fn email) :duplicate
        :default nil))

(defn validate-sign-in-email [email]
  (cond (is-too-long? email email-max-length) :too-long
        (not (is-email-valid? email)) :invalid
        :default nil))

(defn validate-password-format [password]
  (cond (s/blank? password) :blank
        (is-too-long? password password-max-length) :too-long
        (is-too-short? password password-min-length) :too-short
        :default nil))

(defn validate-passwords-are-different [password-1 password-2]
  (if (= password-1 password-2)
    :unchanged
    nil))

(defn validate-email-change [email user-exists?-fn]
  (->
    {:new-email      (validate-registration-email email user-exists?-fn)}
    remove-nil-values))

(defn validate-registration [params user-exists?-fn]
  (let [{:keys [registration-first-name registration-last-name registration-email registration-password]} params]
    (->
      {:registration-first-name (validate-registration-name registration-first-name)
       :registration-last-name  (validate-registration-name registration-last-name)
       :registration-email      (validate-registration-email registration-email user-exists?-fn)
       :registration-password   (validate-password-format registration-password)}
      remove-nil-values)))

(defn validate-user-exists [email user-exists?-fn]
  (when-not (user-exists?-fn email)
    :non-existent))

(defn validate-sign-in [params]
  (let [{:keys [sign-in-email sign-in-password]} params]
    (-> {:sign-in-email    (validate-sign-in-email sign-in-email)
         :sign-in-password (validate-password-format sign-in-password)}
        remove-nil-values)))

(defn validate-correct-password [checker-fn password]
  (when-not (checker-fn password)
    :invalid))

(defn validate-change-password [params check-password-fn]
  (let [{:keys [current-password new-password]} params]
    (-> {:current-password (or (validate-password-format current-password)
                               (validate-correct-password check-password-fn current-password))
         :new-password     (or (validate-password-format new-password)
                               (validate-passwords-are-different current-password new-password))}
        remove-nil-values)))

(defn validate-forgotten-password [params user-exists?-fn]
  (let [email (:email params)]
    (-> {:email (or (validate-sign-in-email email) (validate-user-exists email user-exists?-fn))}
        remove-nil-values)))

(defn validate-reset-password [params]
  (-> {:new-password (validate-password-format (:new-password params))}
      remove-nil-values))
