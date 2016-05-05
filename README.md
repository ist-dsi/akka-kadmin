# akka-kadmin
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/pt.tecnico.dsi/akka-kadmin_2.11/badge.svg?maxAge=604800)](https://maven-badges.herokuapp.com/maven-central/pt.tecnico.dsi/akka-kadmin_2.11)
[![Dependency Status](https://www.versioneye.com/user/projects/5718ed91fcd19a00454417b5/badge.svg?style=plastic&maxAge=604800)](https://www.versioneye.com/user/projects/5718ed91fcd19a00454417b5)
[![Reference Status](https://www.versioneye.com/java/pt.tecnico.dsi:akka-kadmin_2.11/reference_badge.svg?style=plastic&maxAge=604800)](https://www.versioneye.com/java/pt.tecnico.dsi:akka-kadmin_2.11/references)
[![Build Status](https://travis-ci.org/ist-dsi/akka-kadmin.svg?branch=master&style=plastic&maxAge=604800)](https://travis-ci.org/ist-dsi/akka-kadmin)
[![Codacy Badge](https://api.codacy.com/project/badge/coverage/)](https://www.codacy.com/app/IST-DSI/akka-kadmin)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/)](https://www.codacy.com/app/IST-DSI/akka-kadmin)
[![Scaladoc](http://javadoc-badge.appspot.com/pt.tecnico.dsi/akka-kadmin_2.11.svg?label=scaladoc&style=plastic&maxAge=604800)](https://ist-dsi.github.io/akka-kadmin/latest/api/#pt.tecnico.dsi.akka-kadmin.package)
[![license](http://img.shields.io/:license-MIT-blue.svg)](LICENSE)


[Latest scaladoc documentation](http://ist-dsi.github.io/akka-kadmin/latest/api/)

## Install
Add the following dependency to your `build.sbt`:
```sbt
libraryDependencies += "pt.tecnico.dsi" %% "akka-kadmin" % "0.1.0"
```
We use [semantic versioning](http://semver.org).


## Configurations


## How to test akka-kadmin
In the project root run `./test.sh`. This script will run `docker-compose up` inside the docker-kerberos folder.
Be sure to have [docker](https://docs.docker.com/engine/installation/) and [docker-compose](https://docs.docker.com/compose/install/) installed on your computer.

## Note on the docker-kerberos folder
This folder is a [git fake submodule](http://debuggable.com/posts/git-fake-submodules:4b563ee4-f3cc-4061-967e-0e48cbdd56cb)
to the [docker-kerberos repository](https://github.com/ist-dsi/docker-kerberos).

## License
akka-kadmin is open source and available under the [MIT license](LICENSE).