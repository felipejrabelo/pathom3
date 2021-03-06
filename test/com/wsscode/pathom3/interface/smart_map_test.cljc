(ns com.wsscode.pathom3.interface.smart-map-test
  (:require
    [clojure.test :refer [deftest is are run-tests testing]]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.entity-tree :as p.ent]
    [com.wsscode.pathom3.interface.smart-map :as psm]
    [com.wsscode.pathom3.test.geometry-resolvers :as geo]))

(pco/defresolver points-vector [] ::points-vector
  [{:x 1 :y 10}
   {:x 3 :y 11}
   {:x -10 :y 30}])

(pco/defresolver points-set [] ::points-set
  #{{:x 1 :y 10}
    {:x 3 :y 11}
    {:x -10 :y 30}})

(def registry
  [geo/registry geo/geo->svg-registry
   points-vector points-set])

(deftest smart-map-test
  (testing "reading"
    (testing "keyword call read"
      (let [sm (psm/smart-map (pci/register geo/registry)
                 {::geo/left 3 ::geo/width 5})]
        (is (= (::geo/right sm) 8))))

    (testing "get"
      (let [sm (psm/smart-map (pci/register geo/registry)
                 {::geo/left 3 ::geo/width 5})]
        (is (= (get sm ::geo/right) 8))))

    (testing "calling smart map as a fn"
      (let [sm (psm/smart-map (pci/register geo/registry)
                 {::geo/left 3 ::geo/width 5})]
        (is (= (sm ::geo/right) 8)))))

  (testing "assoc uses source context on the new smart map"
    (let [sm (psm/smart-map (pci/register registry)
               {:x 3 :width 5})]
      (is (= (:right sm) 8))
      (is (= (:right (assoc sm :width 10)) 13)))

    (testing "via conj"
      (let [sm (psm/smart-map (pci/register registry)
                 {:x 3 :width 5})]
        (is (= (:right sm) 8))
        (is (= (:right (conj sm [:width 10])) 13)))))

  (testing "dissoc"
    (let [sm (psm/smart-map (pci/register registry)
               {:x 3 :width 5})]
      (is (= (:right sm) 8))
      (is (= (:right (dissoc sm :width)) nil))))

  (testing "nested maps should also be smart maps"
    (let [sm (psm/smart-map (pci/register registry)
               {:x 10 :y 20})]
      (is (= (-> sm ::geo/turn-point :right)
             10))))

  (testing "nested smart maps should return as-is"
    (let [sm-child (psm/smart-map (pci/register registry) {:x 10 :width 20})
          sm       (psm/smart-map {} {:thing sm-child})]
      (is (= (-> sm :thing :right)
             30))))

  (testing "nested maps in sequences should also be smart maps"
    (testing "vector"
      (let [sm (psm/smart-map (pci/register registry)
                 {})]
        (is (vector? (->> sm ::points-vector)))
        (is (= (->> sm ::points-vector (map :left))
               [1 3 -10]))))

    (testing "set"
      (let [sm (psm/smart-map (pci/register registry)
                 {})]
        (is (= (->> sm ::points-set)
               #{{:x 1 :y 10}
                 {:x 3 :y 11}
                 {:x -10 :y 30}}))
        (is (= (->> sm ::points-set first :left)
               3)))))

  (testing "meta"
    (let [sm (-> (pci/register registry)
                 (psm/smart-map {:x 3 :width 5})
                 (with-meta {:foo "bar"}))]
      (is (= (:right sm) 8))
      (is (= (meta sm) {:foo "bar"}))))

  (testing "count, uses the count from cache-tree"
    (let [sm (-> (pci/register registry)
                 (psm/smart-map {:x 3 :width 5}))]
      (is (= (:right sm) 8))
      (is (= (count sm) 7))))

  (testing "keys"
    (testing "using cached keys"
      (let [sm (-> (pci/register registry)
                   (psm/smart-map {:x 3 :width 5}))]
        (is (= (:right sm) 8))
        (is (= (into #{} (keys sm))
               #{:x
                 :width
                 :right
                 ::geo/x
                 ::geo/left
                 ::geo/width
                 ::geo/right}))))

    (testing "using reachable keys"
      (let [sm (-> (pci/register geo/full-registry)
                   (psm/with-keys-mode ::psm/keys-mode-reachable)
                   (psm/smart-map {:x 3}))]
        (is (= (into #{} (keys sm))
               #{:com.wsscode.pathom3.test.geometry-resolvers/x
                 :com.wsscode.pathom3.test.geometry-resolvers/left
                 :x
                 :left}))

        (testing "it should not realize the values just by asking the keys"
          (is (= (-> sm psm/sm-env p.ent/entity)
                 {:x 3})))

        (testing "realizing via sequence"
          (is (= (into {} sm)
                 {:com.wsscode.pathom3.test.geometry-resolvers/left 3
                  :com.wsscode.pathom3.test.geometry-resolvers/x    3
                  :left                                             3
                  :x                                                3}))))))

  (testing "find"
    (let [sm (-> (pci/register registry)
                 (psm/smart-map {:x 3 :width 5}))]
      (is (= (find sm :x) [:x 3])))

    (let [sm (-> (pci/register registry)
                 (psm/smart-map {:not-in-index 42}))]
      (is (= (find sm :not-in-index) [:not-in-index 42])))

    (let [sm (-> (pci/register registry)
                 (psm/smart-map {:x 3 :width 5}))]
      (is (= (find sm :right) [:right 8]))
      (is (= (find sm ::noop) nil)))))

(deftest sm-assoc!-test
  (testing "uses source context on the new smart map"
    (let [sm (psm/smart-map (pci/register registry)
               {:x 3 :width 5})]
      (is (= (:right sm) 8))
      (is (= (:right (psm/sm-assoc! sm :width 10)) 8))
      (is (= (:width sm) 10)))))

(deftest sm-dissoc!-test
  (testing "uses source context on the new smart map"
    (let [sm (psm/smart-map (pci/register registry)
               {:x 3 :width 5})]
      (is (= (:right sm) 8))
      (is (= (:right (psm/sm-dissoc! sm :width)) 8)))))

(deftest sm-touch-test
  (testing "loads data from a EQL expression into the smart map"
    (let [sm (-> (psm/smart-map (pci/register registry)
                   {:x 3 :y 5})
                 (psm/sm-touch! [{::geo/turn-point [:right]}]))]
      (is (= (-> sm psm/sm-env p.ent/entity)
             {:x               3
              :y               5
              ::geo/x          3
              ::geo/left       3
              ::geo/y          5
              ::geo/top        5
              ::geo/turn-point {::geo/right  3
                                ::geo/bottom 5
                                :right       3}})))))

(comment
  (-> (psm/with-keys-mode (pci/register registry) ::psm/keys-mode-reachable)
      (psm/smart-map {:x 3 :width 5})
      keys))
