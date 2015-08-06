(ns stonecutter.test.controller.forgotten-password
  (:require [midje.sweet :refer :all]
            [net.cgrand.enlive-html :as html]
            [stonecutter.test.test-helpers :as th]
            [stonecutter.controller.forgotten-password :as fp]))

(fact "about validation checks on posting email address"
      (-> (th/create-request :post "/forgotten-password" {:email "invalid email"})
          fp/forgotten-password-form-post
          :body
          html/html-snippet
          (html/select [:.form-row--validation-error])) =not=> empty?)