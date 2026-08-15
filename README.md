**Summarized by GitHub Copilot**

**Original:** https://github.com/Project-Genoa/Vienna
# What this is?
An opinionated Java microservice suite for the "Vienna" project — the repository contains an embedded-Tomcat API server (apiserver) that loads static data, connects to a local database, an event-bus, and an object store, and exposes JSON/API routes (signin, resource packs, authenticated endpoints). It appears targeted at backend developers running a local service and other modules in the repo provide supporting pieces (db, eventbus, objectstore, static data, generators).
# Stack
* **Language(s):** Java (primary)
* **Framework / runtime:** JVM (embedded Apache Tomcat), built with Maven (mvn wrapper present)
* **Notable libraries (observed in code):**


* ☞ Jakarta Servlet / embedded Apache Tomcat (server)


* ☞ Apache Commons CLI (command-line parsing)


* ☞ Log4j (logging)


* ☞ Project-local clients/modules: eventbus client, objectstore client, EarthDB (database wrapper)

# How it's organized
```
.pom.xml                      Maven parent build (top-level)
.mvn/                         Maven wrapper support
mvnw, mvnw.cmd                Maven wrapper scripts
apiserver/                    API server module (contains Main.java, routes, routing, utils)
buildplate/                   buildplate-related module (supporting service)
db/                           database module (EarthDB)
eventbus/                     event-bus client/server module
objectstore/                  object storage client/module
staticdata/                   static data and catalog used by apiserver
tappablesgenerator/           generator utility (likely generates tappables/static artifacts)
utils/                        shared utilities
.gitignore
```
How it fits together: The apiserver module is the runtime entry point (Main.java). On start it:

* loads static data from a directory,


* opens/connects to EarthDB,


* creates clients for the event bus and object store,


* constructs an Application and Router, registers subrouters (signin, authenticated, resource packs),


* starts Buildplate-related background handling (BuildplateInstanceRequestHandler / BuildplateInstancesManager),


* and launches an embedded Tomcat instance that delegates incoming HTTP requests to the Application routing layer.
