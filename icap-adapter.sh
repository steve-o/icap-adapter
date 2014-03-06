#!/bin/sh

/usr/lib/jvm/java-1.6.0/bin/java \
	-cp guava-16.0.1.jar:log4j-api-2.0-rc1.jar:log4j-core-2.0-rc1.jar:log4j-jcl-2.0-rc1.jar:commons-cli-1.2.jar:ValueAdd_DomainRep.java6.jar:rfa.java6.jar:target/classes \
	-enableassertions \
	com.uptyc.IcapAdapter.IcapAdapter $*

#java \
#	-cp guava-16.0.1.jar:log4j-api-2.0-rc1.jar:log4j-core-2.0-rc1.jar:log4j-jcl-2.0-rc1.jar:commons-cli-1.2.jar:ValueAdd_DomainRep.jar:rfa.jar:target/classes \
#	-enableassertions \
#	com.uptyc.IcapAdapter.IcapAdapter $*


