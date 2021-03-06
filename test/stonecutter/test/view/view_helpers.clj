(ns stonecutter.test.view.view-helpers
  (:require [midje.sweet :refer :all]
            [net.cgrand.enlive-html :as html]
            [stonecutter.view.view-helpers :as vh]))

(fact "can inject anti-forgery token"
      (let [page (-> "<html><form></form></html>"
                     html/html-snippet)]
        (-> page
            vh/add-anti-forgery
            (html/select [:form (html/attr= :name "__anti-forgery-token")])) =not=> empty?))

(fact "can remove elements from enlive map"
      (let [page (-> "<html><form></form></html>"
                     html/html-snippet)]
        (html/select page [:form]) =not=> empty?
        (-> page
            (vh/remove-element [:form])
            (html/select [:form])) => empty?))

(fact "templates caching"
      (let [file-name "html-file"
            html "some-html"]
        (fact "template are cached when caching is enabled"
              (vh/reset-template-cache!)
              (vh/enable-template-caching!)
              (vh/load-template file-name) => html
              (provided (html/html-resource file-name) => html :times 1)
              (vh/load-template file-name) => html
              (provided (html/html-resource file-name) => html :times 0))
        (fact "if caching is disabled then templates are always loaded from file"
              (vh/disable-template-caching!)
              (vh/load-template file-name) => html
              (provided (html/html-resource file-name) => html :times 1)
              (vh/load-template file-name) => html
              (provided (html/html-resource file-name) => html :times 1))))

(fact "clj-wip class is removed"
     (let [page (-> "<html><p class='clj-wip'>Random element.</p></html>"
                    html/html-snippet)]
       (html/select page [:p]) =not=> empty?
       (-> page
           vh/remove-work-in-progress 
           (html/select [:p])) => empty?))


(fact "helper function for removing attributes"
      (let [page-html "<html a=\"b\"><body a=\"b\"></body></html>"]
        (-> page-html html/html-snippet (vh/remove-attribute [:body] :a) vh/enlive-to-str) => "<html a=\"b\"><body></body></html>"
        (-> page-html html/html-snippet (vh/remove-attribute-globally :a) vh/enlive-to-str) => "<html><body></body></html>"))

(fact "can append script to page"
      (let [page-html "<html><body><h1>Hello</h1></body></html>"]
        (-> page-html html/html-snippet (vh/add-script "js/blah.js") vh/enlive-to-str)
        => "<html><body><h1>Hello</h1><script src=\"js/blah.js\"></script></body></html>"))
