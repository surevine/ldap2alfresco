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
#############################################################################################
#
# For any issues installing this software or configuring it for your environment
# please contact:
#
# Surevine and ask for Support on +44 (0) 845 468 1066 or e-mail us on support@surevine.com
#
#############################################################################################
  

ldap2alfresco
-------------

Synchronises members of LDAP groups (whose group names begin with a specified prefix)
to members of the corresponding Alfresco site (whose site names are the same but without
the prefix).

If a group member is also in a special Administrators group then they become a Manager
of the Alfresco site, otherwise they become a Collaborator.

Also synchronises Alfresco open markings groups and closed markings groups from LDAP
groups with a specified prefix.

The tool stores a timestamp in LDAP and by default only synchronises groups which have
changed since the start of the last run (NB. if a group changes during a run then it
will also be synchronised during the next run, but this is not harmful).

If the administrator group changes then all groups are completely synchronised since it
is impossible to know if an Administrator has been deleted, so all permissions must be
refreshed in all groups.

A lock entry is written into LDAP at the start of a run and deleted at the end of a run.
If the tool exits with an error then this lock is left in place.  This indicates that
the timestamp entry is inconsistent, and an incremental synchronisation is not possible.

To force a full update run the tool with a single command line parameter "force".

If the lock entry is present in LDAP then the tool will exit with an error message
indicating that it must be run in force mode.

(NB. It may be desireable to change this behaviour so that it runs in force mode
automatically if the lock file is present - but this should only be done if we can
guarantee that only one copy will run at a time.  To change this you would need to edit
Ldap2alfresco.java.)

Files
-----

	lib
		directory containing all our code and 3rd party libraries

	ldap2alfresco.properties
		Sample properties file to be edited by the user, this must be in the
		CLASSPATH specified in ldap2alfresco.sh (default is the current directory).

	ldap2alfresco.sh
		Shell script to execute the JAR file.  Takes an optional "force"
		parameter.  Edit the script to set the location of the keystore for SSL
		certificates used by the CAS server.


