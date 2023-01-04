#!/usr/bin/env -S bb --classpath src

(require '[cheshire.core :as json]
         '[clojure.test :refer [deftest is run-tests]]
         '[util])

(def base-url "https://www.btgpactual.cl/")

(def funds-symbol->path {"CFIBTGRCA" "renta-comercial/"
                         "CFIBTGRRA" "enta-residencial/"
                         "CFIREMERGE" "infraestructura/"
                         "CFIBTGCYFA" "credito-y-facturas/"
                         "CFIBTGFGIA" "financiamiento-con-garantias-inmobiliarias/"
                         "CFIMHEA-E" "mhe-habitacional/"})

(defn extract-quotes
  [data]
  (->> data :perf :performance :entry
       (map (fn [q] {:date (-> q :key (subs 0 10))
                     :value (-> q :value bigdec)}))))

(deftest extract-quotes-test
  (is (= [{:date "2020-01-01", :value 1.5M}
          {:date "2020-01-02", :value 2M}]
         (extract-quotes {:perf {:performance {:entry [{:key "2020-01-01T00:00:00-3", :value 1.5}
                                                       {:key "2020-01-02T00:00:00-3", :value 2}]}}}))))

(defn merge-quotes
  [old new]
  (->> (merge (into {} (map (juxt :date :value) old))
              (into {} (map (juxt :date :value) new)))
       (map (fn [[date value]]
              {:date date
               :value value}))
       (sort-by :date)))

(deftest merge-quotes-test
  (is (= [{:date 1 :value 100}, {:date 2 :value 150}, {:date 3 :value 200}])
      (merge-quotes [{:date 1 :value 100}, {:date 2 :value 120}]
                    [{:date 2 :value 150}, {:date 3 :value 200}]))
  (is (= [{:date 3 :value 200}, {:date 4 :value 500}])
      (merge-quotes nil [{:date 3 :value 200}, {:date 4 :value 500}])))

(defn -main
  []
  (doseq [[sym path] funds-symbol->path]
    (let [json-filename (str "out/" sym ".json")
          page (util/fetch-url! (str base-url path))
          [_ json-data] (re-find #"var jsonFondos = '(.*)';" page)
          new-quotes (extract-quotes (json/parse-string json-data true))
          old-quotes (try (json/parse-string (slurp json-filename) true)
                          (catch java.io.FileNotFoundException _ex
                            (println "Warning. File not found:" json-filename)
                            nil))
          quotes (->> (merge-quotes old-quotes new-quotes)
                      (map (fn [q] (assoc q :symbol sym))))]
      (util/write-json! json-filename quotes)
      (util/write-ledger-prices! (str "out/ledger/" sym ".price") quotes "CLP"))))

(if (= "test" (first *command-line-args*))
  (let [{:keys [:fail :error]} (run-tests)]
    (System/exit (+ fail error)))
  (-main))
