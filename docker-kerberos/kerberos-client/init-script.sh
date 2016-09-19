#!/bin/bash

source `dirname $0`/configureKerberosClient.sh

cd /tmp/akka-kadmin

#sbt <<<"testOnly *RemoveAndResendSpec"
#sbt <<<"testOnly *DeduplicationSpec"
#sbt <<<"testOnly *PolicySpec"
sbt clean test
#sbt -Dfile.encoding=UTF8 coverageReport codacyCoverage

if [[ -z $CI ]] || [[ "$CI" == "false" ]]; then
  chown -R $HOST_USER_ID .
fi