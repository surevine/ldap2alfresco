#-------------------------------------------------------------------------------
# Copyright (C) 2008-2010 Surevine Limited.
#   
# Although intended for deployment and use alongside Alfresco this module should
# be considered 'Not a Contribution' as defined in Alfresco'sstandard contribution agreement, see
# http://www.alfresco.org/resource/AlfrescoContributionAgreementv2.pdf
# 
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#-------------------------------------------------------------------------------
#!/bin/sh
#
#	ldap2alfresco.sh [force]
#
# Does an incremental synchronisation from LDAP groups to Alfresco
# sites, or optionally a full sychronisation with the 'force' parameter.
#
# All settings are read from ldap2alfresco.properties in the CLASSPATH (which
# is set by default to be the current directory).
#
# If the CAS server uses an SSL certificate which is not signed by a CA
# which Java trusts, then the certificate must be imported into the user's
# keystore, e.g. /root/.keystore for root.
#
# To import the certificate:
# keytool -importcert -alias cas -file localhost.crt
# If the certificate is not accepted, check that it doesn't contain any
# additional text in the file, as this causes problems with keytool. 

KEYSTORE_FILE=/root/.keystore
KEYSTORE_PASSWORD=@replaceme@

ROOT_DIR=.

LIB_DIR=$ROOT_DIR/lib

JAR_ALFRESCO=$LIB_DIR/alfresco_connect_lib-1.3.0.jar
JAR_LDAP2ALFRESCO=$LIB_DIR/ldap2alfresco-${project.version}.jar

JAR_HTTP_CLIENT=$LIB_DIR/httpclient-4.0.1.jar
JAR_HTTP_CORE=$LIB_DIR/httpcore-4.0.1.jar
JAR_JSON=$LIB_DIR/json-20080701.jar
JAR_COMMONS_LOGGING=$LIB_DIR/commons-logging-1.1.1.jar
JAR_COMMONS_CODEC=$LIB_DIR/commons-codec-1.3.jar
JAR_COMMONS_LANG=$LIB_DIR/commons-lang-2.5.jar
JAR_LOG4J=$LIB_DIR/log4j-1.2.14.jar

CP="$ROOT_DIR:$JAR_ALFRESCO:$JAR_LDAP2ALFRESCO:$JAR_HTTP_CLIENT:$JAR_HTTP_CORE:$JAR_JSON:$JAR_COMMONS_LOGGING:$JAR_COMMONS_CODEC:$JAR_COMMONS_LANG:$JAR_LOG4J"
    
java -Djavax.net.ssl.trustStore=$KEYSTORE_FILE \
     -Djavax.net.ssl.trustStorePassword=$KEYSTORE_PASSWORD \
     -cp "$CP" \
     com.surevine.ldap2alfresco.Ldap2alfresco $*
