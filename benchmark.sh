#!/bin/sh

set -x
mvn \
	exec:java \
	-Dexec.mainClass="com.google.caliper.runner.CaliperMain" \
	-Dexec.classpathScope="test" \
	-Dexec.args="com.uptyc.IcapAdapter.TibMsgBenchmark --vm=jdk6,jdk7" \
	-Dhttps.proxyHost=10.1.51.10 -Dhttps.proxyPort=80 -Dhttp.proxyHost=10.1.51.10 -Dhttp.proxyPort=80

