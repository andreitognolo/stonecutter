(ns stonecutter.integration.kerodon.email-confirmations
  (:require [midje.sweet :refer :all]
            [kerodon.core :as k]
            [clojure.java.io :as io]
            [stonecutter.email :as email]
            [stonecutter.integration.kerodon.kerodon-checkers :as kc]
            [stonecutter.integration.kerodon.kerodon-selectors :as ks]
            [stonecutter.integration.integration-helpers :as ih]
            [stonecutter.routes :as routes]
            [stonecutter.logging :as l]
            [stonecutter.integration.kerodon.steps :as steps]))

(l/init-logger!)

(defn parse-test-email []
  (read-string (slurp "test-tmp/test-email.txt")))

(defn delete-directory [directory-path]
  (->> (io/file directory-path)
       file-seq
       reverse
       (map io/delete-file)
       doall))

(defn setup-test-directory [state]
  (fact {:midje/name "setup test tmp directory"}
        (io/make-parents "test-tmp/dummy.txt")
        (.exists (io/file "test-tmp")) => true)
  state)

(defn teardown-test-directory [state]
  (fact {:midje/name "teardown test tmp directory"}
        (delete-directory "test-tmp")
        (.exists (io/file "test-tmp")) => false)
  state)

(defn test-email-renderer [email-data]
  {:subject ""
   :body    (str email-data)})

(background (email/get-confirmation-renderer) => test-email-renderer)

(def email-sender (email/bash-sender-factory "test-resources/mail_stub.sh"))

(def test-app (ih/build-app {:email-sender email-sender}))

(defn debug [state number]
  (prn number)
  (prn state)
  state)

(facts "User is not confirmed when first registering for an account; Hitting the confirmation endpoint confirms the user account when the UUID in the uri matches that for the signed in user's account"
       (-> (k/session test-app)

           (setup-test-directory)

           (steps/register "confirmation-test@email.com" "valid-password")

           (k/visit (routes/path :show-profile))
           (kc/selector-exists [ks/profile-unconfirmed-email-message])

           (k/visit (routes/path :confirm-email-with-id
                                 :confirmation-id (get-in (parse-test-email) [:body :confirmation-id])))
           (kc/check-and-follow-redirect)
           (kc/page-route-is :show-profile)
           (kc/selector-exists [ks/profile-flash-message])

           (teardown-test-directory)))

(facts "The account confirmation flow can be followed by a user who is not signed in when first accessing the confirmation endpoint"
       (-> (k/session test-app)

           (setup-test-directory)

           (steps/register "confirmation-test-2@email.com" "valid-password")
           (k/visit "/profile")
           (k/follow ks/sign-out-link)
           (kc/check-and-follow-redirect "just signed out")

           (k/visit (routes/path :confirm-email-with-id
                                 :confirmation-id (get-in (parse-test-email) [:body :confirmation-id])))
           (kc/page-uri-is (routes/path :confirm-email-with-id
                                        :confirmation-id (get-in (parse-test-email) [:body :confirmation-id])))

           (kc/check-and-follow-redirect "redirecting to sign in")
           (kc/page-uri-is (routes/path :confirmation-sign-in-form
                                        :confirmation-id (get-in (parse-test-email) [:body :confirmation-id])))
           (kc/check-and-fill-in ks/confirmation-sign-in-password-input "valid-password")
           (kc/check-and-press ks/sign-in-submit)

           (kc/check-and-follow-redirect "redirecting to email confirmation")
           (kc/page-uri-is (routes/path :confirm-email-with-id
                                        :confirmation-id (get-in (parse-test-email) [:body :confirmation-id])))

           (kc/check-and-follow-redirect "redirecting to profile page")
           (kc/page-route-is :show-profile)
           (kc/selector-exists [ks/profile-flash-message])

           (teardown-test-directory)))

(facts "A user can follow the delete-account flow from a confirmation email"
       (-> (k/session test-app)

           (setup-test-directory)

           (steps/register "confirmation-test-3@email.com" "valid-password")
           (k/visit "/profile")
           (k/follow ks/sign-out-link)
           (kc/check-and-follow-redirect "just signed out")

           (k/visit (routes/path :show-confirmation-delete
                                 :confirmation-id (get-in (parse-test-email) [:body :confirmation-id])))
           (kc/page-uri-is (routes/path :show-confirmation-delete
                                        :confirmation-id (get-in (parse-test-email) [:body :confirmation-id])))
           (kc/response-status-is 200)
           (kc/selector-exists [ks/delete-account-page-body])
           (kc/check-and-press ks/delete-account-button)

           (kc/check-and-follow-redirect)
           (kc/check-page-is :show-profile-deleted [ks/profile-deleted-page-body])

           (teardown-test-directory)))
