# akka-kadmin
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pt.tecnico.dsi/akka-kadmin_2.12/badge.svg?maxAge=604800)](https://maven-badges.herokuapp.com/maven-central/pt.tecnico.dsi/akka-kadmin_2.12)
[![Dependency Status](https://www.versioneye.com/user/projects/572b59e6a0ca350050840794/badge.svg?style=flat-square&maxAge=604800)](https://www.versioneye.com/user/projects/572b59e6a0ca350050840794)
[![Reference Status](https://www.versioneye.com/java/pt.tecnico.dsi:akka-kadmin_2.12/reference_badge.svg?style=plastic&maxAge=604800)](https://www.versioneye.com/java/pt.tecnico.dsi:akka-kadmin_2.12/references)
[![Build Status](https://travis-ci.org/ist-dsi/akka-kadmin.svg?branch=master&style=plastic&maxAge=604800)](https://travis-ci.org/ist-dsi/akka-kadmin)
[![Codacy Badge](https://api.codacy.com/project/badge/coverage/f24fdb8cf47a4db180c9187c476b23f0)](https://www.codacy.com/app/IST-DSI/akka-kadmin)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/f24fdb8cf47a4db180c9187c476b23f0)](https://www.codacy.com/app/IST-DSI/akka-kadmin)
[![Scaladoc](http://javadoc-badge.appspot.com/pt.tecnico.dsi/akka-kadmin_2.12.svg?label=scaladoc&style=plastic&maxAge=604800)](https://ist-dsi.github.io/kadmin/latest/api/pt/tecnico/dsi/kadmin/akka/index.html)
[![license](http://img.shields.io/:license-MIT-blue.svg)](LICENSE)

[Latest scaladoc documentation](https://ist-dsi.github.io/kadmin/latest/api/pt/tecnico/dsi/kadmin/akka/index.html)

The kadmin actor will perform a best effort deduplication. To do so the code **assumes** there is only **one**
kadmin actor interacting with kerberos.

By best effort we mean that there is still a very small window in which duplication can still occur.
To understand it, we must first explain how the code operates:
The kadmin actor knows the last deliveryId it handled for a given sender.
When a new message arrives it will check its deliveryId against the expectedId (last deliveryId + 1) of that sender.
 - If (deliveryId > expectedId) then

   The message is a future one and there are unprocessed messages in between, so the actor will ignore it.
 - If (deliveryId < expectedId) then

   The message is a resend, the actor will just respond with its result.
   This result was stored in the actor internal state when the message side-effect was performed.
 - If (deliveryId == expectedId) then

   This is the message we are expecting, so the side-effect will performed the following way:
    1. Store **in memory** that we started performing the side-effect.
    2. Perform the side-effect.
    3. Persist the side-effect result, and then in the persist handler, update
       the actor internal state with the result.

If the actor crashes:
 - After performing the side-effect
 - But before persisting its result

Duplication will occur because when recovering the actor won't know that it already performed the side-effect.
This is also true if we persisted that we started executing the side-effect.

For most operations this won't be a problem because they are idempotent. However for ChangePassword and
consequently AddPrincipal (since ChangePassword might be invoked inside AddPrincipal) this might be a problem
because these operations might not be idempotent in some cases.

## Install
Add the following dependency to your `build.sbt`:
```sbt
libraryDependencies += "pt.tecnico.dsi" %% "akka-kadmin" % "0.5.0"
```
We use [semantic versioning](http://semver.org).

## Configurations
akka-kadmin uses [typesafe-config](https://github.com/typesafehub/config).

The [reference.conf](src/main/resources/reference.conf) file has the following keys:
```scala
akka-kadmin {
  # How long to wait to actually perform the remove.
  # It is useful to perform the remove at a later time to deal with delayed messages.
  # If set to 0 then the remove will be performed instantaneously
  remove-delay = 15 minutes

  # Every X messages a snapshot will be saved. Set to 0 to disable automatic saving of snapshots.
  # This value is not a strict one because the kadmin actor will not persist it.
  # If 190 messages were already processed and then the actor crashs only after
  # another 200 messages will the actor save a snapshot.
  # Repeated and future messages will not count. Or, in other words, only messages where the deliveryId
  # is equal to the expectedId will increase the counter.
  save-snapshot-every-X-messages = 200

  # Akka-kadmin will use as settings for kadmin library those defined:
  # 1) Here, directly under the path akka-kadmin (these have precedence over the next ones).
  # 2) On the same level as akka-kadmin.
}
```

Alternatively you can pass your Config object to the Kadmin actor directly, or subclass the
[Settings](https://ist-dsi.github.io/kadmin/latest/api/pt/tecnico/dsi/kadmin/akka/Settings.html) class for a mixed approach.
The scaladoc of the Settings class has examples explaining the different options.

## How to test akka-kadmin
In the project root run `./test.sh`. This script will run `docker-compose up` inside the docker-kerberos folder.
Be sure to have [docker](https://docs.docker.com/engine/installation/) and [docker-compose](https://docs.docker.com/compose/install/) installed on your computer.

## Note on the docker-kerberos folder
This folder is a [git fake submodule](http://debuggable.com/posts/git-fake-submodules:4b563ee4-f3cc-4061-967e-0e48cbdd56cb)
to the [docker-kerberos repository](https://github.com/ist-dsi/docker-kerberos).

## License
akka-kadmin is open source and available under the [MIT license](LICENSE).