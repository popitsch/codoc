#!/bin/bash
if [ $# -lt 1 ]; then
  echo "usage: build.sh <version>"
  echo the version should match the entry in the pom.xml file.
  head pom.xml
  exit 1
fi
export MAVEN_OPTS=-Xss2m
mvn $2 clean assembly:assembly -U -DskipTests=true -Denv=dev
cp target/codoc-$1-jar-with-dependencies.jar bin/codoc-$1.jar
cp target/codoc-$1-tests.jar bin/codoc-$1-tests.jar
cp target/codoc-$1.jar bin/codoc-$1-library.jar
