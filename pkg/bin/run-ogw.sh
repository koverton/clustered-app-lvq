#!/bin/bash
cd `dirname $0`/..
basedir=`pwd`

cp=.
for F in lib/*.*; do
	cp=$cp:$F
done

java -cp $cp com.solacesystems.demo.MockOrderGateway $*
