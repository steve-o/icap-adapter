#!/bin/sh

# Apache Commons command-line-processor
COMMONSCLI=commons-cli-1.2.jar

# Google Guava library
GUAVA=guava-16.0.1.jar

# Apache LOG4J2 and friends
# per http://logging.apache.org/log4j/2.x/faq.html#which_jars
JAVAUTILLOGGINGAPI=jul-to-slf4j-1.7.6.jar
SLF4JAPI=slf4j-api-1.7.6.jar
SLF4JBINDING=log4j-slf4j-impl-2.0-rc1.jar
LOG4J2=log4j-api-2.0-rc1.jar:log4j-core-2.0-rc1.jar:$SLF4JBINDING:$SLF4JAPI:$JAVAUTILLOGGINGAPI
##LOG4J2=log4j-api-2.0-rc1.jar:log4j-core-2.0-rc1.jar

# Thomson Reuters RFA and ValueAdd libraries
RFA6=rfa.java6.jar
RFAVALUEADD6=ValueAdd_DomainRep.java6.jar

RFA=rfa.jar
RFAVALUEADD=ValueAdd_DomainRep.jar

/usr/lib/jvm/java-1.6.0/bin/java \
	-cp $COMMONSCLI:$GUAVA:$LOG4J2:$RFA6:$RFAVALUEADD6:target/classes \
	-enableassertions \
	com.uptyc.IcapAdapter.IcapAdapter $*

#java \
#	-cp $COMMONSCLI:$GUAVA:$LOG4J2:$RFA:$RFAVALUEADD:target/classes \
#	-enableassertions \
#	com.uptyc.IcapAdapter.IcapAdapter $*


