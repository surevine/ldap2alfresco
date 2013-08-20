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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.surevine.alfresco.AlfrescoConnector;
import com.surevine.alfresco.AlfrescoException;
import com.surevine.alfresco.PropertyException;
import com.surevine.alfresco.PropertyWrapper;

/**
 * Class to update a number of profile fields.
 */
public class ProfileUpdater {

    /**
     * A list of profile fields to update.
     */
    private Collection<ProfileFieldConverter> profileFields;

    /**
     * Logging instance.
     */
    private static final Logger LOGGER = Logger.getLogger(ProfileUpdater.class);

    /**
     * Construct a ProfileUpdater loading the fields to synchronise from a PropertyWrapper.
     * @param properties Where to load the fields from
     * @throws PropertyException if profile fields are not defined correctly in the properties file
     */
    public ProfileUpdater(final PropertyWrapper properties) throws PropertyException {

        profileFields = getProfileFields(properties, "syncField");
    }

    /**
     * Update the profile fields into LDAP for a single user.
     * @param ldap To connect to LDAP
     * @param username User to update
     * @param fields Contains entries for every field listed in properties file
     * @throws LdapException On any LDAP errors
     */
    public void updateSingleUserToLdap(
            final LdapConnector ldap,
            final String username,
            final JSONObject fields) throws LdapException {

        Attributes attributes = new BasicAttributes();

        // encode each profile field in turn
        Iterator<ProfileFieldConverter> fieldIter = profileFields.iterator();

        while (fieldIter.hasNext()) {
            ProfileFieldConverter converter = fieldIter.next();
            converter.toAttributes(attributes, fields);
        }

        ldap.updateUser(username, attributes);
    }

    /**
     * Update Alfresco user profile fields from LDAP user attributes.  If any users
     * cannot be updated they are logged and skipped.
     * @param ldap To connect to LDAP
     * @param alfresco To connect to Alfresco
     * @param allUsers Set to true to update all records, not just records changed since the last run
     * @param lastRun Timestamp of the last run (string representation as returned by LDAP server)
     * @throws LdapException On any LDAP errors
     * @throws AlfrescoException On any fatal Alfresco errors
     */
    public void updateFromLdapToAlfresco(
            final LdapConnector ldap,
            final AlfrescoConnector alfresco,
            final boolean allUsers,
            final String lastRun)
            throws LdapException, AlfrescoException {

        // get groups
        Collection<Attributes> users = null;

        if (allUsers) {
            users = ldap.getAllUsers();
        } else {
            users = ldap.getModifiedUsers(lastRun);
        }

        LOGGER.info("Found " + users.size() + " users to synchronise");

        Iterator<Attributes> userIter = users.iterator();

        while (userIter.hasNext()) {
            // get the user
            Attributes userAttributes = userIter.next();

            Attribute cn = userAttributes.get("cn");

            if (cn != null) {
                String username;

                try {
                    username = cn.get().toString();
                } catch (NamingException e1) {
                    username = null;
                }

                if (username != null) {
                    LOGGER.info("Synchronising " + username);

                    JSONObject fields = new JSONObject();

                    // encode each profile field in turn
                    Iterator<ProfileFieldConverter> fieldIter = profileFields.iterator();

                    while (fieldIter.hasNext()) {
                        ProfileFieldConverter converter = fieldIter.next();
                        converter.toJson(fields, userAttributes);
                    }

                    try {
                        alfresco.updateProfile(username, fields);
                    } catch (AlfrescoException e) {
                        recoverFromExceptionUser(ldap, e, username);
                    }
                }
            }
        }
    }
    /**
     * Return a list of profile fields from the properties file.
     * @param properties Properties to look in
     * @param fieldPrefix Prefix for all properties which represent a profile field
     * @return List of profile field converters
     * @throws PropertyException if profile fields are not defined correctly in the properties file
     */
    private Collection<ProfileFieldConverter> getProfileFields(
            final PropertyWrapper properties,
            final String fieldPrefix) throws PropertyException {

        final String typeLabel = "type";
        final String multipleLabel = "multiple";
        final String ldapNameLabel = "ldapName";
        final String alfrescoNameLabel = "alfrescoName";

        Collection<ProfileFieldConverter> fields = new LinkedList<ProfileFieldConverter>();

        // looking for e.g. syncField.type.something
        Collection<String> keys = properties.getKeys("^" + fieldPrefix + "\\." + typeLabel + "\\..+");

        Iterator<String> iter = keys.iterator();

        while (iter.hasNext()) {
            String typeKey = iter.next();
            String attributeName = typeKey.replaceFirst("^" + fieldPrefix + "\\." + typeLabel + "\\.", "");

            String type = properties.getProperty(typeKey);

            boolean multiple = properties.getProperty(
                                    fieldPrefix + "." + multipleLabel + "." + attributeName).equalsIgnoreCase("yes");

            String ldapName = properties.getProperty(fieldPrefix + "." + ldapNameLabel + "." + attributeName);

            String alfrescoName = properties.getProperty(fieldPrefix + "." + alfrescoNameLabel + "." + attributeName);

            LOGGER.info("Loading profile field converter: " + ldapName + " -> " + alfrescoName);

            if (type.equalsIgnoreCase("text")) {
                fields.add(new ProfileFieldTextConverter(ldapName, alfrescoName, multiple));
            } else if (type.equalsIgnoreCase("telephone")) {
                fields.add(new ProfileFieldTelephoneConverter(ldapName, alfrescoName, multiple));
            } else {
                LOGGER.log(Level.ERROR, "Unrecognised profile field type: " + type);
            }
        }

        return fields;
    }

    /**
     * Attempt to recover from an exception by updating the timestamp of
     * the user being processed so it gets processed next time.  If that
     * fails then a new exception is thrown, which will cause processing
     * to be aborted, and the next run will identify the failure due to
     * the lock entry still being present and disallow an incremental
     * update.
     * @param ldap To connect to LDAP
     * @param e The exception that caused the problem
     * @param username The LDAP user being processed
     * @throws LdapException If the LDAP user cannot be modified
     */
    private void recoverFromExceptionUser(
            final LdapConnector ldap,
            final Exception e,
            final String username)
            throws LdapException {
        logException(Level.ERROR, e);

        // mark user as modified so it gets tried again next time round
        ldap.touchUser(username);
    }

    /**
     * Output an exception's stack trace to the log file.
     * @param level The log level
     * @param e The exception
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
