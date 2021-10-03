(ns util
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]
            [clojure.string :as string]))

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

(defn- quote->ledger-price
  [quote reference-symbol]
  (format "%s price %s %27.6f %s" (:date quote) (string/replace (:symbol quote) #" " "-") (bigdec (:value quote)) reference-symbol))

(defn write-ledger-prices!
  [filename data reference-symbol]
  (println "Writing" filename)
  (->> (map #(quote->ledger-price % reference-symbol) data)
       (string/join "\n")
       (spit filename)))

(defn fetch-url!
  [url]
  (println "Downloading" url)
  (:body (curl/get url)))
