/*
 * Copyright (C) 2008-2010 Surevine Limited.
 *   
 * Although intended for deployment and use alongside Alfresco this module should
 * be considered 'Not a Contribution' as defined in Alfresco'sstandard contribution agreement, see
 * http://www.alfresco.org/resource/AlfrescoContributionAgreementv2.pdf
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
package com.surevine.ldap2alfresco;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.surevine.alfresco.AlfrescoConnector;
import com.surevine.alfresco.AlfrescoException;
import com.surevine.alfresco.Authenticator;
import com.surevine.alfresco.CasAuthenticator;
import com.surevine.alfresco.PropertyException;
import com.surevine.alfresco.PropertyWrapper;

/**
 * Synchronise groups in an LDAP server with sites in an Alfresco server.
 */
public class Ldap2alfresco {

	/**
	 * Name of properties file to configure the application.
	 */
	static final String PROPERTIES_FILENAME = "ldap2alfresco";

	/**
	 * Connector to the LDAP server.
	 */
	private LdapConnector ldap;

	/**
	 * Connector to the Alfresco server.
	 */
	private AlfrescoConnector alfresco;

	/**
	 * To update profile fields.
	 */
	private ProfileUpdater profileUpdater;

	/**
	 * A list of users to add to every site.
	 */
	private Collection<String> extraUsers;

	/**
	 * Marking used for open security groups in Alfresco.
	 */
	private String alfrescoMarkingsOpen;

	/**
	 * Marking used for organisational security groups in Alfresco
	 */
	private String alfrescoMarkingsOrg;
	
	/**
	 * Marking used for closed security groups in Alfresco.
	 */
	private String alfrescoMarkingsClosed;

	/**
	 * What do you add to the end of a regular site name to get the name of the
	 * deleted items site?
	 */
	private String deletedItemsPostfix;

	/**
	 * The name of the role in Alfresco that defines users with special delete
	 * privs
	 */
	private String deletersRoleName;

	/**
	 * Logging instance.
	 */
	private static final Logger LOGGER = Logger.getLogger(Ldap2alfresco.class);

	/**
	 * Run the application.
	 * 
	 * @param args
	 *            Command line arguments
	 */
	public static void main(final String[] args) {
		boolean force = false;

		if (args.length > 0 && args[0].equalsIgnoreCase("force")) {
			force = true;
		}

		Ldap2alfresco ldap2alf = new Ldap2alfresco(PROPERTIES_FILENAME);
		ldap2alf.update(force);

		// This could be uncommented to test inserting data into LDAP
		// TestRichProfiles.testUpdateToLdap(PROPERTIES_FILENAME);
	}

	/**
	 * @param propertiesFilename
	 *            Name of properties file to configure the application
	 */
	public Ldap2alfresco(final String propertiesFilename) {
		PropertyWrapper properties;

		try {
			properties = new PropertyWrapper(propertiesFilename);

			extraUsers = getExtraUsers(properties.getProperty("extra.users"));

			deletedItemsPostfix = properties.getProperty("alfresco.deleted.items.postfix");

			deletersRoleName = properties.getProperty("alfresco.deleters.role.name");

			alfrescoMarkingsOpen = properties.getProperty("alfresco.markings.open");
			
			alfrescoMarkingsOrg = properties.getProperty("alfresco.markings.org");

			alfrescoMarkingsClosed = properties.getProperty("alfresco.markings.closed");

			profileUpdater = new ProfileUpdater(properties);

			ldap = new LdapConnector(properties);

			Authenticator auth = new CasAuthenticator(properties);
			alfresco = new AlfrescoConnector(properties, auth);

		} catch (AlfrescoException e) {
			// any exception in constructing the object is fatal
			logException(Level.FATAL, e);
			System.exit(1);
		} catch (PropertyException e) {
			// any exception in constructing the object is fatal
			logException(Level.FATAL, e);
			System.exit(1);
		} catch (LdapException e) {
			// any exception in constructing the object is fatal
			logException(Level.FATAL, e);
			System.exit(1);
		}
	}

	/**
	 * Update Alfresco from LDAP.
	 * 
	 * @param force
	 *            Set to true to update all records, not just records changed
	 *            since the last run
	 */
	public void update(final boolean force) {
		boolean forceGroupUpdate = force;

		try {
			// check for lock
			if (!force && ldap.lockExists()) {
				LOGGER.fatal("LDAP contains a lock entry - previous run did not complete successfully." + "Re-run with 'force' argument to force a full update");
				return;
			}

			// make lock
			ldap.createLock();

			// get timestamp of last run and update it
			String lastRun = ldap.updateTimestamp();

			// if admin group timestamp has changed then any of the users
			// could have changed roles, so we must force all users to be
			// re-added to their groups to set their roles correctly
			if (ldap.haveAdminsChanged(lastRun)) {
				LOGGER.info("Admin group has been modified, forcing a full update of groups");
				forceGroupUpdate = true;
			}

			// update sites
			updateSites(forceGroupUpdate, lastRun);

			// update security groups
			updateSecurityGroups(forceGroupUpdate, lastRun, LdapConnector.GroupType.OPEN);
			updateSecurityGroups(forceGroupUpdate, lastRun, LdapConnector.GroupType.CLOSED);
			updateSecurityGroups(forceGroupUpdate, lastRun, LdapConnector.GroupType.ORG);

			// Update 'Deleters' statuses
			// As there's only one deleters group and it's behaviour is
			// dependant upon what _other_ groups users are in,
			// we refresh the whole deleters piece each time
			updateDeleters();

			// update profile fields
			profileUpdater.updateFromLdapToAlfresco(ldap, alfresco, force, lastRun);

			// got to here so safe to delete lock
			ldap.deleteLock();

			// don't delete lock if we get an exception as we haven't finished
			// the update so the synchronisation is in a bad state and we need
			// to force a full update
		} catch (LdapException e) {
			logException(Level.FATAL, e);
		} catch (AlfrescoException e) {
			logException(Level.FATAL, e);
		}
	}

	/**
	 * Parse out a comma-separated string of users into a list.
	 * 
	 * @param extraUsersProperty
	 *            Comma-separated list of users
	 * @return List of users
	 */
	private Collection<String> getExtraUsers(final String extraUsersProperty) {
		// parse list of users
		Collection<String> users = new HashSet<String>();

		if (extraUsersProperty != null) {
			String extraUsersPropertyTrimmed = extraUsersProperty.trim();

			if (extraUsersPropertyTrimmed.length() > 0) {
				String[] userArray = extraUsersPropertyTrimmed.split(",");
				for (int x = 0; x < userArray.length; x++) {
					users.add(userArray[x]);
				}
			}
		}

		return users;
	}

	private void updateDeleters() throws LdapException, AlfrescoException {

		// First off get from ldap and alfresco their understanding of deleters
		Collection<String> currentLdapDeletersGroup = ldap.getDeleters();
		Collection<String> currentAlfrescoDeletersGroup = alfresco.getMembershipOfGroup(deletersRoleName);		

		// Now identify new deleters from ldap.
		Collection<String> addToDeleters = new ArrayList<String>(currentLdapDeletersGroup);
		addToDeleters.removeAll(currentAlfrescoDeletersGroup);
		
		// Now identify old deleters from alfresco
		Collection<String> removeFromDeleters = new ArrayList<String>(currentAlfrescoDeletersGroup);
		removeFromDeleters.removeAll(currentLdapDeletersGroup);

		
		// Do the additions first.
		for (String newDeleterUsername : addToDeleters) {
			
			LOGGER.info("Adding " + newDeleterUsername + " to " + deletersRoleName);

			alfresco.addMemberToGroup(newDeleterUsername, deletersRoleName);
			Iterator<String> siteMemberships = ldap.getSiteMemberships(newDeleterUsername).iterator();
			while (siteMemberships.hasNext()) {
				String siteName = siteMemberships.next();
				alfresco.addMemberToSite(siteName + deletedItemsPostfix, newDeleterUsername, AlfrescoConnector.RoleType.MANAGER);
			}
		}
		
		// Now do the removal
		for (String oldDeleterUsername : removeFromDeleters) {
			LOGGER.info("Removing " + oldDeleterUsername + " from " + deletersRoleName);

			Iterator<String> siteMemberships = ldap.getSiteMemberships(oldDeleterUsername).iterator();

			while (siteMemberships.hasNext()) {
				String siteName = siteMemberships.next();
				alfresco.removeMemberFromSiteIfPresent(siteName + deletedItemsPostfix, oldDeleterUsername);
			}
			
			alfresco.removeMemberFromGroupIfPresent(oldDeleterUsername, deletersRoleName);
		}
	}

	/**
	 * Update Alfresco sites from LDAP groups. If any users cannot be updated
	 * they are logged and skipped.
	 * 
	 * @param force
	 *            Set to true to update all records, not just records changed
	 *            since the last run
	 * @param lastRun
	 *            Timestamp of the last run (string representation as returned
	 *            by LDAP server)
	 * @throws LdapException
	 *             On any LDAP errors
	 * @throws AlfrescoException
	 *             On any fatal Alfresco errors
	 */
	private void updateSites(final boolean force, final String lastRun) throws LdapException, AlfrescoException {
		// get list of admins
		Collection<String> admins = ldap.getAdmins();

		// get groups
		Collection<String> groups = null;

		if (force) {
			groups = ldap.getAllGroups(LdapConnector.GroupType.SITE);
		} else {
			groups = ldap.getModifiedGroups(LdapConnector.GroupType.SITE, lastRun);
		}

		Iterator<String> groupIter = groups.iterator();

		while (groupIter.hasNext()) {
			// get the group
			String group = groupIter.next();

			// get the member lists for the group from LDAP and Alfresco
			Collection<String> ldapMembers = ldap.getGroupMembers(group, LdapConnector.GroupType.SITE);
			Collection<String> alfMembers = alfresco.getSiteMemberList(group);

			// add in extra users
			ldapMembers.addAll(extraUsers);

			// work out who needs to be deleted from Alfresco
			Collection<String> toDelete = new HashSet<String>(alfMembers);
			toDelete.removeAll(ldapMembers);

			// work out who needs to be added to Alfresco
			Collection<String> toAdd = new HashSet<String>(ldapMembers);
			if (!force) {
				toAdd.removeAll(alfMembers);
			}

			// do the additions first (in case the deletions delete
			// the last admin from the site)
			Iterator<String> addIter = toAdd.iterator();
			while (addIter.hasNext()) {
				String member = addIter.next();

				AlfrescoConnector.RoleType role = AlfrescoConnector.RoleType.COLLABORATOR;

				if (admins.contains(member)) {
					role = AlfrescoConnector.RoleType.MANAGER;
				}

				LOGGER.info("Adding " + member + " to " + group + " as " + role);

				try {
					alfresco.addMemberToSite(group, member, role);
				} catch (AlfrescoException e) {
					recoverFromException(e, group, LdapConnector.GroupType.SITE);
				}
			}

			// do the deletions
			Iterator<String> delIter = toDelete.iterator();
			while (delIter.hasNext()) {
				String member = delIter.next();
				LOGGER.info("Deleting " + member + " from " + group);

				try {
					alfresco.removeMemberFromSite(group, member);
				} catch (AlfrescoException e) {
					recoverFromException(e, group, LdapConnector.GroupType.SITE);
				}
			}
		}
	}

	/**
	 * Update Alfresco security groups from LDAP groups. If any users cannot be
	 * updated they are logged and skipped.
	 * 
	 * @param force
	 *            Set to true to update all records, not just records changed
	 *            since the last run
	 * @param lastRun
	 *            Timestamp of the last run (string representation as returned
	 *            by LDAP server)
	 * @param gt
	 *            The type of security group to update
	 * @throws LdapException
	 *             On any LDAP errors
	 */
	private void updateSecurityGroups(final boolean force, final String lastRun, final LdapConnector.GroupType gt) throws LdapException {
		Collection<String> groups = null;

		if (force) {
			groups = ldap.getAllGroups(gt);
		} else {
			groups = ldap.getModifiedGroups(gt, lastRun);
		}

		Iterator<String> groupIter = groups.iterator();

		while (groupIter.hasNext()) {
			// get the group
			String group = groupIter.next();

			try {
				// get the member lists for the group from LDAP
				Collection<String> members = ldap.getGroupMembers(group, gt);

				// add in extra users
				members.addAll(extraUsers);

				LOGGER.info("Setting security group: " + group + " to: " + members.toString());

				if (gt == LdapConnector.GroupType.OPEN) {
					alfresco.updateRmConstraint(alfrescoMarkingsOpen, group, members);
				} else if (gt == LdapConnector.GroupType.CLOSED) {
					alfresco.updateRmConstraint(alfrescoMarkingsClosed, group, members);
				} else if (gt == LdapConnector.GroupType.ORG) {
					alfresco.updateRmConstraint(alfrescoMarkingsOrg, group, members);
				} else {
					LOGGER.error("Incorrect security group type");
				}
			} catch (LdapException e) {
				recoverFromException(e, group, gt);
			} catch (AlfrescoException e) {
				recoverFromException(e, group, gt);
			}
		}
	}

	/**
	 * Attempt to recover from an exception by updating the timestamp of the
	 * group being processed so it gets processed next time. If that fails then
	 * a new exception is thrown, which will cause processing to be aborted, and
	 * the next run will identify the failure due to the lock entry still being
	 * present and disallow an incremental update.
	 * 
	 * @param e
	 *            The exception that caused the problem
	 * @param group
	 *            The LDAP group being processed
	 * @param gt
	 *            The type of group
	 * @throws LdapException
	 *             If the LDAP group cannot be modified
	 */
	private void recoverFromException(final Exception e, final String group, final LdapConnector.GroupType gt) throws LdapException {
		logException(Level.ERROR, e);

		// mark group as modified so it gets tried again next time round
		ldap.touchGroup(group, gt);
	}

	/**
	 * Output an exception's stack trace to the log file.
	 * 
	 * @param level
	 *            The log level
	 * @param e
	 *            The exception
	 */
	private void logException(final Level level, final Exception e) {
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			LOGGER.log(level, sw.toString());
		} catch (Exception e2) {
			LOGGER.log(level, "stack trace unavailable");
		}
	}

}
