#!/usr/bin/env -S bb --classpath src

(require '[clojure.data.csv :as csv]
         '[clojure.string :as string]
         '[clojure.test :refer [deftest is run-tests]]
         '[util])

(defn- extract-values
  [symbol column rows]
  (->> (map (fn [row]
              (let [date-str (first row)
                    value-str (-> (nth row column)
                                  (string/replace "." "")
                                  (string/replace "," "."))]
                (when (and (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}" date-str)
                           (not (string/blank? value-str)))
                  {:date date-str
                   :value (bigdec value-str)
                   :symbol symbol})))
            rows)
       (filter some?)))

(deftest extract-values-test
  (is (= [{:symbol "FOO-A" :date "2020-01-01" :value 10.1M}
          {:symbol "FOO-A" :date "2020-01-02" :value 20M}
          {:symbol "FOO-A" :date "2020-01-03" :value 3000.04M}]
         (extract-values "FOO-A"
                         2
                         [["2020-01-01" "" "10,1" ""]
                          ["2020-01-02" "" "20" ""]
                          ["2020-01-03" "" "3.000,04"]]))))

(defn- parse-chunk
  [series chunk]
  (let [[header _ & values] chunk
        afp-names (->> header rest (take-nth 2))]
    (->> afp-names
         (map-indexed
           (fn [ix afp-name]
             (extract-values (str afp-name "-" series)
                             (inc (* 2 ix))
                             values)))
         (apply concat))))

(deftest parse-chunk-test
  (is (= [{:date "2020-01-01" :symbol "FOO-A" :value 1M}
          {:date "2020-01-02" :symbol "FOO-A" :value 5M}
          {:date "2020-01-01" :symbol "BAR-A" :value 3M}
          {:date "2020-01-02" :symbol "BAR-A" :value 7M}]
         (parse-chunk "A"
                      [["Fecha" "FOO" "" "BAR" ""]
                       ["ignore" "me"]
                       ["2020-01-01" "1" "2" "3" "4"]
                       ["2020-01-02" "5" "6" "7" "8"]]))))

(defn parse-csv
  [series csv]
  (let [rows (csv/read-csv csv :separator \;)
        ; number of columns varies, so, for processing, we have to split this stuff into chunks
        ; which in the source are delimited by empty rows
        chunks (->> rows
                    (partition-by (fn [row] (= (count row) 1)))
                    (filter (fn [chunk] (> (count chunk) 3))))]
    (->> (map (partial parse-chunk series) chunks)
         ; then merge them back together
         (apply concat))))

(deftest parse-csv-test
  (let [parsed (parse-csv "A" (slurp "test-resources/vcfA2019.csv"))]
    (is (= 2282 (count parsed)))
    (is (= 105146880.51M (->> parsed (map :value) (apply +))))
    (is (= -1835640849 (hash parsed)))))

(defn fetch-csv!
  [start-year end-year series]
  (util/fetch-url! (str "https://www.spensiones.cl/apps/valoresCuotaFondo/vcfAFPxls.php?"
                        "aaaaini=" start-year
                        "&aaaafin=" end-year
                        "&tf=" series
                        "&fecconf=" end-year "1231")))

(defn -main
  []
  (let [quotes (->> ["A" "B" "C" "D" "E"]
                    (map (fn [series]
                           (->> series
                                (fetch-csv! 2002 (util/format-date-now "YYYY"))
                                (parse-csv series))))
                    (apply concat))]
    (doseq [[sym quotes] (group-by :symbol quotes)]
      (util/write-json! (str "out/" sym ".json")
                        quotes)
      (util/write-ledger-prices! (str "out/ledger/" sym ".price")
                                 quotes
                                 "CLP"))))

(if (= "test" (first *command-line-args*))
  (let [{:keys [:fail :error]} (run-tests)]
    (System/exit (+ fail error)))
  (-main))
