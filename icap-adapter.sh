#!/bin/sh

JAVA=java
JAVA=/usr/lib/jvm/java-1.6.0/bin/java
JAVA=/home/sumologic/jre1.6.0_26/bin/java

#ICAP=target/classes
ICAP=target/icap-adapter-1.2-SNAPSHOT.jar

# Apache Commons command-line-processor
COMMONSCLI=commons-cli-1.2.jar

# Google Guava library
GUAVA=guava-16.0.1.jar
GSON=gson-2.2.4.jar

JODA=joda-time-2.3.jar

# Apache LOG4J2 and friends
# per http://logging.apache.org/log4j/2.x/faq.html#which_jars
JAVAUTILLOGGINGAPI=jul-to-slf4j-1.7.6.jar
SLF4JAPI=slf4j-api-1.7.6.jar
SLF4JBINDING=log4j-slf4j-impl-2.0-rc1.jar
LOG4J2=log4j-api-2.0-rc1.jar:log4j-core-2.0-rc1.jar
LOG4J2=log4j-api-2.0-rc1.jar:log4j-core-2.0-rc1.jar:$SLF4JBINDING:$SLF4JAPI:$JAVAUTILLOGGINGAPI

# Thomson Reuters RFA and ValueAdd libraries
RFA=rfa.jar
RFA=rfa.java6.jar

RFAVALUEADD=ValueAdd_DomainRep.jar
RFAVALUEADD=ValueAdd_DomainRep.java6.jar

set -x
$JAVA \
	-cp $ICAP:$COMMONSCLI:$GUAVA:$GSON:$JODA:$LOG4J2:$RFA:$RFAVALUEADD \
	-enableassertions \
	-Dlog4j.configurationFile=log4j2.xml \
	com.uptyc.IcapAdapter.IcapAdapter $*

