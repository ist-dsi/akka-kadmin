#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/akka-kadmin

#sbt <<<"testOnly *RemoveAndResendSpec"
#sbt <<<"testOnly *DeduplicationSpec"
#sbt <<<"testOnly *PolicySpec"
sbt clean coverage test coverageReport codacyCoverage

if [[ -z $CI ]] || [[ "$CI" == "false" ]]; then
  chown -R $HOST_USER_ID .
fi