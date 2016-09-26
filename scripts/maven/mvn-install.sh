#!/bin/bash -x
ver=7.2.0.544

jar=sol-jms-$ver.jar
pom=sol-jms-$ver.pom
mvn install:install-file -Dfile=$jar -DpomFile=$pom

jar=sol-common-$ver.jar
pom=sol-common-$ver.pom
mvn install:install-file -Dfile=$jar -DpomFile=$pom

jar=sol-jcsmp-$ver.jar
pom=sol-jcsmp-$ver.pom
mvn install:install-file -Dfile=$jar -DpomFile=$pom

