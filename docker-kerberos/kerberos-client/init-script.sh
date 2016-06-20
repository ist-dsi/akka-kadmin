#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/akka-kadmin

sbt <<<"testOnly pt.tecnico.dsi.kadmin.akka.DeduplicationSpec"
#sbt clean coverage test coverageReport codacyCoverage