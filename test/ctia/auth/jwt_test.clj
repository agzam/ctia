(ns ctia.auth.jwt-test
  (:require [ctia.auth.jwt :as sut]
            [ctia.auth.capabilities :as caps]
            [ctia.properties :as p]
            [clojure.test :as t :refer [deftest is]]
            [clojure.set :as set])
  (:import [ctia.auth.jwt JWTIdentity]))

(def get-in-config
  p/get-in-global-properties)

(deftest wrap-jwt-to-ctia-auth-test
  (let [handler (fn [r]
                  {:body r
                   :status 200})
        wrapped-handler (sut/wrap-jwt-to-ctia-auth handler get-in-config)
        request-no-jwt {:body "foo"
                        :url "http://localhost:8080/foo"}
        request-jwt (assoc request-no-jwt
                           :jwt {:sub "subject name"
                                 (sut/iroh-claim "org/id" get-in-config) "organization-id"})
        response-no-jwt (wrapped-handler request-no-jwt)
        response-jwt (wrapped-handler request-jwt)]
    (is (= {:body {:body "foo"
                   :url "http://localhost:8080/foo"}
            :status 200}
           response-no-jwt))
    (is (= {:body
            {:body "foo"
             :url "http://localhost:8080/foo"
             :jwt {:sub "subject name"
                   (sut/iroh-claim "org/id" get-in-config) "organization-id"}
             :groups ["organization-id"]
             :login  "subject name"}
            :status 200}
           (update response-jwt :body dissoc :identity)))
    (is (instance? ctia.auth.jwt.JWTIdentity
                   (get-in response-jwt [:body :identity])))))

(deftest scopes-to-capapbilities-test
  (is (= "private-intel" (sut/entity-root-scope get-in-config))
      "entity root scope default value is private-intel")
  (is (= "casebook" (sut/casebook-root-scope get-in-config))
      "casebook root scope default value is casebook")
  (is (= #{:search-casebook :create-casebook :list-casebooks :read-casebook
           :delete-casebook}
         (sut/scope-to-capabilities (sut/casebook-root-scope get-in-config) get-in-config))
      "Check the casebook capabilities from the casebook scope")
  (is (= #{:developer :specify-id}
         (set/difference caps/all-capabilities
                         (sut/scopes-to-capabilities #{(sut/entity-root-scope get-in-config)
                                                       (sut/casebook-root-scope get-in-config)}
                                                     get-in-config)))
      "with all scopes you should have most capabilities except some very
       specific ones")

  (is (set/subset?
       (sut/scopes-to-capabilities #{(sut/casebook-root-scope get-in-config)}
                                   get-in-config)
       (sut/scopes-to-capabilities #{(sut/entity-root-scope get-in-config)}
                                   get-in-config))
      "casebook capabilities are a subset of all entity caps")

  (is (= #{:import-bundle}
         (sut/scopes-to-capabilities
          #{(str (sut/entity-root-scope get-in-config) "/import-bundle")}
          get-in-config)))
  (is (= #{}
         (sut/scopes-to-capabilities
          #{(str (sut/entity-root-scope get-in-config) "/import-bundle:read")}
          get-in-config)))
  (is (contains? (sut/scopes-to-capabilities #{(str (sut/entity-root-scope get-in-config) ":write")}
                                             get-in-config)
                 :import-bundle))
  (is (not (contains? (sut/scopes-to-capabilities #{(str (sut/entity-root-scope get-in-config) ":read")}
                                                  get-in-config)
                      :import-bundle)))
  (is (= #{:read-sighting :list-sightings :search-sighting :create-sighting
           :delete-sighting}
         (sut/scopes-to-capabilities
          #{(str (sut/entity-root-scope get-in-config) "/sighting")}
          get-in-config))
      "Scopes can be limited to some entity")
  (is (= #{:create-sighting :delete-sighting}
         (sut/scopes-to-capabilities
          #{(str (sut/entity-root-scope get-in-config) "/sighting:write")}
          get-in-config))
      "Scopes can be limited to some entity and for write-only")
  (is (= #{:read-sighting :list-sightings :search-sighting}
         (sut/scopes-to-capabilities
          #{(str (sut/entity-root-scope get-in-config) "/sighting:read")}
          get-in-config))
      "Scopes can be limited to some entity and for read-only")
  (is (= #{:search-casebook :read-sighting :list-sightings :search-sighting
           :list-casebooks :read-casebook}
         (sut/scopes-to-capabilities #{(str (sut/entity-root-scope get-in-config) "/sighting:read")
                                       (str (sut/casebook-root-scope get-in-config) ":read")}
                                     get-in-config))
      "Scopes can compose"))

(deftest parse-jwt-pubkey-map-test
  (is (= ["APP ONE"]
         (keys
          (sut/parse-jwt-pubkey-map "APP ONE=resources/cert/ctia-jwt.pub")))
      "Should be able to parse and load the keys without exception when the keys exists")
  (is (= ["APP ONE" "APP ONE TEST"]
         (keys
          (sut/parse-jwt-pubkey-map "APP ONE=resources/cert/ctia-jwt.pub,APP ONE TEST=resources/cert/ctia-jwt-2.pub")))
      "Should be able to parse and load the keys without exception when the keys exists")

  (is (= (str "Could not load JWT keys correctly."
              " Please check ctia.http.jwt.jwt-pubkey-map config:"
              " /bad.key (No such file or directory)")
         (try
           (sut/parse-jwt-pubkey-map "APP ONE=/bad.key")
           (catch Exception e
             (.getMessage e))))
      "Should be able to parse and load the keys without exception when the keys exists")

  (is (= (str "Wrong format for ctia.http.jwt.jwt-pubkey-map config."
              " It should matches the following regex:"
              " ^([^=,]*=[^,]*)(,[^=,]*=[^,]*)*$"
              "\nExamples: \"APPNAME=/path/to/file.key\""
              "\n          \"APPNAME=/path/to/file.key,OTHER=/other/path.key\"")
         (try
           (sut/parse-jwt-pubkey-map "APP ONE resources/cert/wrong-jwt.pub")
           (catch Exception e
             (.getMessage e))))
      "Should returns a correct error message when failing to load the key"))
