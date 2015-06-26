(ns stonecutter.handler
  (:require [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as r]
            [ring.adapter.jetty :refer [run-jetty]]
            [bidi.bidi :refer [path-for]]
            [environ.core :refer [env]]
            [scenic.routes :refer [scenic-handler]]
            [stonecutter.view.register :as register]
            [stonecutter.view.sign-in :as sign-in]
            [stonecutter.view.error :as error]
            [stonecutter.view.view-helpers :refer [enable-template-caching! disable-template-caching!]]
            [stonecutter.translation :refer [load-translations-from-file]]
            [stonecutter.validation :as v]
            [stonecutter.storage :as s]
            [stonecutter.routes :refer [routes path]]
            [stonecutter.logging :as log-config]
            [clojure.tools.logging :as log]
            [stonecutter.controller.oauth :as oauth]
            [clauth.token :as token]
            [clauth.client :as client]))

(def translation-map
  (load-translations-from-file "en.yml"))

(defn translations-fn [translation-map]
  (fn [translation-key]
    (let [key1 (keyword (namespace translation-key))
          key2 (keyword (name translation-key))
          translation (get-in translation-map [key1 key2])]
      (when-not translation (log/warn (str "No translation found for " translation-key)))
      translation)))

(defn html-response [s]
  (-> s
      r/response
      (r/content-type "text/html")))

(defn show-registration-form [request]
  (let [context {:translator (translations-fn translation-map)}]
    (html-response (register/registration-form context))))

(defn show-sign-in-form [request]
  (let [context {:translator (translations-fn translation-map)}]
    (html-response (sign-in/sign-in-form context))))

(defn register-user [request]
  (let [params (:params request)
        email (:email params)
        password (:password params)
        err (v/validate-registration params s/is-duplicate-user?)
        context {:translator (translations-fn translation-map)
                 :errors err
                 :params params}]
    (if (empty? err)
      (do
        (s/store-user! email password)
        (html-response "You saved the user"))
      (html-response (register/registration-form context)))))

(defn show-profile [request]
  (if-let [email (get-in request [:session :user :email])]
    (html-response (str "You are signed in as " email))
    (r/redirect (path :sign-in))))

(defn redirect-to-authorisation [return-to user client-id email]
  (if-let [client (client/fetch-client client-id)]
    (assoc (r/redirect return-to) :session {:user user :access_token (:token (token/create-token client email))})
    (throw (Exception. "Invalid client"))))

(defn redirect-to-profile [user]
  (assoc (r/redirect (path :show-profile)) :session {:user user}))

(defn sign-in [request]
  (let [params (:params request)
        client-id (get-in request [:session :client-id])
        return-to (get-in request [:session :return-to])
        email (:email params)
        password (:password params)
        err (v/validate-sign-in params)
        context {:translator (translations-fn translation-map)
                 :params params
                 :errors err}]
    (if (empty? err)
      (if-let [user (s/authenticate-and-retrieve-user email password)]
        (cond (and client-id return-to) (redirect-to-authorisation return-to user client-id email)
               client-id                (throw (Exception. "Missing return-to value"))
              :default                  (redirect-to-profile user))
        (r/status (->> {:credentials :invalid}
                       (assoc context :errors)
                       sign-in/sign-in-form
                       html-response)
                400))
      (r/status (->> context sign-in/sign-in-form html-response) 400))))

(defn not-found [request]
  (let [context {:translator (translations-fn translation-map)}]
    (-> (html-response (error/not-found-error context))
        (r/status 404))))

(defn home [request]
  (if (get-in request [:session :user])
    (r/redirect (path :show-profile))
    (r/redirect (path :sign-in))))

(def handlers
  {:home home
   :show-registration-form show-registration-form
   :register-user register-user
   :show-sign-in-form show-sign-in-form
   :sign-in sign-in
   :show-profile show-profile})

(def app-handler
  (scenic-handler routes handlers not-found))

(defn wrap-error-handling [handler]
  (let [context {:translator (translations-fn translation-map)}]
    (fn [request]
      (try
        (handler request)
        (catch Exception e
          (log/error e)
          (-> (html-response (error/internal-server-error context)) (r/status 500)))))))

(def wrap-defaults-config
  (-> site-defaults
      (assoc-in [:session :cookie-attrs :max-age] 3600)))

(def app (wrap-defaults app-handler wrap-defaults-config))

(def port (Integer. (get env :port "3000")))

(defn -main [& args]
  (log-config/init-logger!)
  (enable-template-caching!)
  (s/setup-mongo-stores! (get env :mongo-uri "mongodb://localhost:27017/stonecutter"))
  (-> app wrap-error-handling (run-jetty {:port port})))

(defn lein-ring-init
  "Function called when running app with 'lein ring server'"
  []
  (log-config/init-logger!)
  (disable-template-caching!)
  (s/setup-in-memory-stores!))
