(ns util
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]))

(defn format-date-now
  [pattern]
  (let [now (java.time.ZonedDateTime/now)
        formatter (java.time.format.DateTimeFormatter/ofPattern pattern)]
    (.format now formatter)))

(defn write-json!
  [filename data]
  (println "Writing" filename)
  (->> (json/generate-string data {:pretty true})
       (spit filename)))

(defn fetch-url!
  [url]
  (println "Downloading" url)
  (:body (curl/get url)))