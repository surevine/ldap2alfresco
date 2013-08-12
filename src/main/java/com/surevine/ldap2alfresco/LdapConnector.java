/*
 * Copyright (C) 2010 Surevine Ltd.
 *
 * All rights reserved.
 */

package com.surevine.ldap2alfresco;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.TimeZone;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import com.surevine.alfresco.PropertyException;
import com.surevine.alfresco.PropertyWrapper;

/**
 * Connect to an LDAP server.
 */
public class LdapConnector {
    /**
     * Different types of LDAP groups (which determines the
     * prefix on the LDAP name).
     */
    public static enum GroupType {
        /**
         * Corresponds to an Alfresco site.
         */
        SITE,
        /**
         * Corresponds to an Alfresco open security group.
         */
        OPEN,
        /**
         * Corresponds to an Alfresco closed security group.
         */
        CLOSED,
        /**
         * Corresponds to an Alfresco organisational security group.
         */
        ORG
    };

    /**
     * Initial size of hash sets (based on typical group size).
     */
    private static final int HASH_SIZE = 128;

    /**
     * Object which connections to LDAP and allows searching.
     */
    private DirContext       ldapDirectoryContext = null;

    /**
     * Location of groups in LDAP.
     */
    private String           ldapGroups;

    /**
     * Location of users in LDAP.
     */
    private String           ldapUsers;

    /**
     * objectClass in LDAP for profile fields.
     */
    private String           ldapProfileObjectClass;

    /**
     * Name of the LDAP group which lists the admins.
     */
    private String           ldapGroupAdmins;
    
    /**
     * Name of the LDAP group which lists the deleters.
     */
    private String           ldapGroupDeleters;

    /**
     * Name of an entity to be created in the LDAP root context
     * for a timestamps to enable incremental updates.
     */
    private String           ldapTimestamp;

    /**
     * Name of an entity to be created in the LDAP root context
     * for a lock to enable incremental updates.
     */
    private String           ldapLock;

    /**
     * Prefix to LDAP groups to indicate correspondence with
     * Alfresco sites.
     */
    private String           ldapGroupPrefix;

    /**
     * Prefix to LDAP groups to indicate correspondence with
     * Alfresco open markings.
     */
    private String           ldapGroupPrefixOpen;

    /**
     * Prefix to LDAP groups to indicate correspondence with
     * Alfresco closed markings.
     */
    private String           ldapGroupPrefixClosed;
    
    /**
     * Prefix to LDAP groups to indicate correspondence with
     * Alfresco organisational markings.
     */
    private String           ldapGroupPrefixOrg;
    
    /**
     * Root context everything lives under
     */
    private String ldapRootContext;
    
    /**
     * Do we return a 304 if the security model is not modified?
     */
    private boolean securityModel304;
    
    /**
     * Set of properties describing how to connect to LDAP
     */
    protected Properties 		 ldapEnv;
    
    /**
     * Last updated date for the security model.
     */
    private static Date SECURITY_MODEL_LAST_UPDATED;
    
    /**
     * Date formats for LDAP.
     */
    private static final SimpleDateFormat LDAP_DATE_FORMAT;
    
    /**
     * Static initializer.
     */
    static {
    	LDAP_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
    	LDAP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * @param properties Contains configuration for the LDAP server to connect to
     * @throws LdapException If required properties are not present or if authentication fails
     */
    public LdapConnector(final PropertyWrapper properties) throws LdapException {
        String ldapHostname;
        String ldapRootDn;
        String ldapRootPassword;

        try {
            // read properties needed in the constructor
            ldapHostname = properties.getProperty("ldap.hostname");
            ldapRootContext = properties.getProperty("ldap.root.context");
            ldapRootDn = properties.getProperty("ldap.root.dn");
            ldapRootPassword = properties.getProperty("ldap.root.password");

            // read and save properties needed elsewhere
            ldapGroups = properties.getProperty("ldap.groups");
            ldapUsers = properties.getProperty("ldap.users");
            ldapProfileObjectClass = properties.getProperty("ldap.profile.objectClass");
            ldapGroupAdmins = properties.getProperty("ldap.group.admins");
            ldapGroupDeleters = properties.getProperty("ldap.group.deleters");
            ldapTimestamp = properties.getProperty("ldap.timestamp");
            ldapLock = properties.getProperty("ldap.lock");
            ldapGroupPrefix = properties.getProperty("ldap.group.prefix");
            ldapGroupPrefixOpen = properties
                    .getProperty("ldap.group.prefix.open");
            ldapGroupPrefixClosed = properties
                    .getProperty("ldap.group.prefix.closed");
            ldapGroupPrefixOrg = properties
            .getProperty("ldap.group.prefix.org");
            
            // do we 304 the security if not modified?
            securityModel304 = !properties.getProperty("alfresco.securitymodel.304notmodified").equalsIgnoreCase("false");
        } catch (PropertyException e) {
            throw new LdapException("Cannot find a required property", e);
        }

        // set up LDAP environment
        ldapEnv = new Properties();
        ldapEnv.put(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.ldap.LdapCtxFactory");
        ldapEnv.put(Context.PROVIDER_URL, "ldap://" + ldapHostname + "/"
                + ldapRootContext);
        ldapEnv.put(Context.SECURITY_PRINCIPAL, ldapRootDn);
        ldapEnv.put(Context.SECURITY_CREDENTIALS, ldapRootPassword);    
    }
    
    protected DirContext getDirectoryContext() throws LdapException
    {
    	//This class is intended for batch operation so we don't need to manage loads of synchronised blocks here
    	if (ldapDirectoryContext==null)
    	{
    		// obtain initial directory context using the environment
    		try {
    			ldapDirectoryContext = new InitialDirContext(ldapEnv);
    		} catch (NamingException e) {
    			throw new LdapException("Cannot connect to LDAP server", e);
    		}
    	}
    	return ldapDirectoryContext;
    }
    
    protected void releaseContext(DirContext ctx)
    {
    	//This class re-uses the connection so we just do nothing here
    }

    /**
     * Get a list of members from an LDAP group with a prefix
     * determined by the group type.
     * @param groupName Name of the group
     * @param gt Type of the group
     * @return List of members
     * @throws LdapException On any LDAP error
     */
    public Collection<String> getGroupMembers(final String groupName, final GroupType gt)
            throws LdapException {
        String filter = "cn=" + getPrefix(gt) + groupName;
        // allow groups not in the top of the tree
        return getGroupMembersFromFilter(filter, SearchControls.SUBTREE_SCOPE);
    }

    /**
     * "Touch" a group, i.e. change its modification date without
     * actually modifying the contents of the group.
     * @param groupName Name of the group
     * @param gt Type of the group
     * @throws LdapException On any LDAP error
     */
    public void touchGroup(final String groupName, final GroupType gt) throws LdapException {
        String prefixedGroupName = getPrefix(gt) + groupName;

        // change cn (group name) - but give it the same name, this
        // will update the time stamp

        BasicAttribute attr = new BasicAttribute("cn");
        attr.add(prefixedGroupName);

        BasicAttributes attributes = new BasicAttributes();
        attributes.put(attr);

        String fullname = "cn=" + prefixedGroupName + "," + ldapGroups;

        DirContext ctx = getDirectoryContext();
        try 
        {
        	ctx.modifyAttributes(fullname,
                    DirContext.REPLACE_ATTRIBUTE, attributes);
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Cannot change LDAP modification date on "
                    + prefixedGroupName, e);
        }
        finally
        {
        	releaseContext(ctx);
        }
        
    }

    /**
     * "Touch" a user, i.e. change its modification date without
     * actually modifying its contents.
     * @param username Name of the user
     * @throws LdapException On any LDAP error
     */
    public void touchUser(final String username) throws LdapException {

        // change cn (user name) - but give it the same name, this
        // will update the time stamp

        BasicAttribute attr = new BasicAttribute("cn");
        attr.add(username);

        BasicAttributes attributes = new BasicAttributes();
        attributes.put(attr);

        String fullname = getModifiableDN(username);

        DirContext ctx = getDirectoryContext();
        try {
        	ctx.modifyAttributes(fullname,
                    DirContext.REPLACE_ATTRIBUTE, attributes);
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Cannot change LDAP modification date on " + username, e);
        }
        finally
        {
        	releaseContext(ctx);
        }
    }

    /**
     * Update specified profile fields for a user.
     * @param username The user to update
     * @param attributes The attributes to change
     * @throws LdapException On any LDAP error
     */
    public void updateUser(final String username, final Attributes attributes) throws LdapException {

        String fullname = getModifiableDN(username);

        DirContext ctx = getDirectoryContext();
        try {
            // first we need to make sure the user has the correct objectClass
            // for Rich Profiles
            addRichProfileToUser(username);

            // now do the updates
            ctx.modifyAttributes(fullname,
                    DirContext.REPLACE_ATTRIBUTE, attributes);
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Cannot update LDAP attributes on user: "
                    + username, e);
        }
        finally
        {
        	releaseContext(ctx);
        }
    }

    /**
     * Add a rich profile to a user if they don't already have one.
     * @param username User to modify
     * @throws NamingException on any errors
     */
    private void addRichProfileToUser(final String username) throws NamingException, LdapException {

        if (!userHasRichProfile(username)) {

            String ouName = getModifiableDN(username);

            final ModificationItem[] classModification = new ModificationItem[1];

            classModification[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
                        new BasicAttribute("objectclass", ldapProfileObjectClass));

            DirContext ctx = getDirectoryContext();
            try 
            {
            	ctx.modifyAttributes(ouName, classModification);
            }
            finally
            {
            	releaseContext(ctx);
            }
            
        }
    }

    /**
     * Does the user already have a rich profile?
     * @param username User to check
     * @return Does the user already have a rich profile?
     * @throws NamingException on any errors
     */
    private boolean userHasRichProfile(final String username) throws NamingException, LdapException {

        SearchResult result = getSingleSearchResultObjectforUser(username);

        Attributes attr = result.getAttributes();
		
		Attribute attribute = attr.get("objectClass");
		
		return attribute.contains(ldapProfileObjectClass);
    }

    /**
     * Get a list of the admins from the LDAP admins group.
     * @return List of usernames
     * @throws LdapException On any LDAP error
     */
    public Collection<String> getAdmins() throws LdapException {
        String filter = "cn=" + ldapGroupAdmins;
        // only one admin group, at the top
        return getGroupMembersFromFilter(filter, SearchControls.ONELEVEL_SCOPE);
    }
    
    /**
     * Get a list of the deleters from the LDAP deleters group.
     * @return List of usernames
     * @throws LdapException On any LDAP error
     */
    public Collection<String> getDeleters() throws LdapException {
        String filter = "cn=" + ldapGroupDeleters;
        // only one admin group, at the top
        return getGroupMembersFromFilter(filter, SearchControls.ONELEVEL_SCOPE);
    }
    
    /**
     * Get the list of sites a user is a member of
     * @param principalName Name of a user in LDAP
     * @return Collection of Strings representing the names of sites the input user is a member of
     * @throws LdapException 
     */
    public Collection<String> getSiteMemberships(String userName) throws LdapException {
    	String filter = "(&(cn="+ldapGroupPrefix+"*)(member="+getFullDnForUser(userName)+")) ";
    	
    	SearchControls controls = new SearchControls();
    	controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    	
    	Enumeration<SearchResult> objects; //Yeah, an Enumeration.  It's like we're from History
    	DirContext ctx = getDirectoryContext();
    	try {
    		Collection<String> rVal = new ArrayList<String>(2);
    		objects = ctx.search(ldapGroups, filter, controls);

    		while (objects.hasMoreElements()) {
    			String siteName = objects.nextElement().getName();
    			siteName=siteName.substring(3+ldapGroupPrefix.length()); //3 is cn=, the -1 is 'cause indexes start at 0
    			rVal.add(siteName);
    		}
    		return rVal;
    	}
    	catch (NamingException e) {
    		throw new LdapException("Cannot retrieve groups for "+userName+" from Ldap", e);
    	}
    	finally {
    		releaseContext(ctx);
    	}
    }
    
    /**
     * Given the full user name of a user ie. "simonw-org1" or "richardl-org2" without any ldap specific stuff,
     * look-up the full Dn of that user.  Need to use SUBTREE_SCOPE as there is a hierarchy.
     * @param userName
     * @return
     */
    protected String getFullDnForUser(String userName) throws LdapException {
    	
        try {
            String userNameFilter = "(cn=" + userName + ")";
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    
            Enumeration<SearchResult> objects;
            objects = this.getDirectoryContext().search(ldapUsers, userNameFilter, controls);
    
            if (!objects.hasMoreElements()) {
                throw new LdapException("Could not find the user with sid: " + userName);
            }
            
            SearchResult result = objects.nextElement();

            if (objects.hasMoreElements()) {
                throw new LdapException("Found multiple users with the sid: "+ userName);
            }
            
            return result.getNameInNamespace();
            
        } catch (NameNotFoundException nnfe) {
            String message = "User not found in LDAP";
            throw new LdapException(message, nnfe);
        } catch (NamingException ne) {
            String message = "Error occurred in LDAP lookup";
            throw new LdapException(message, ne);
        }
    }

    
    /**
     * Based on a username get access to the single result.
     * 
     * @param username
     * @return the search result with a single object for the username.
     * @throws LdapException
     */
    public SearchResult getSingleSearchResultObjectforUser(String username) throws LdapException {
        try {
            String filter = "(cn=" + username + ")";
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    
            Enumeration<SearchResult> objects;
            objects = this.getDirectoryContext().search(ldapUsers, filter, controls);
    
            if (!objects.hasMoreElements()) {
                throw new LdapException("Could not find the user with sid: " + username);
            }
            
            SearchResult result = objects.nextElement();

            if (objects.hasMoreElements()) {
                throw new LdapException("Found multiple users with the sid: "+ username);
            }
            
            return result;
            
        } catch (NameNotFoundException nnfe) {
            String message = "User not found in LDAP";
            throw new LdapException(message, nnfe);
        } catch (NamingException ne) {
            String message = "Error occurred in LDAP lookup";
            throw new LdapException(message, ne);
        }    	
    }
    /**
     * Has the LDAP admins group been modified since a specified time?
     * @param timestamp The time to compare with
     * @return True if the group has changed, otherwise false
     * @throws LdapException On any LDAP error
     */
    public boolean haveAdminsChanged(final String timestamp) throws LdapException {
        String filter1 = "cn=" + ldapGroupAdmins;
        String filter2 = "modifyTimestamp>=" + timestamp;
        String filter = "(&(" + filter1 + ")(" + filter2 + "))";

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);

        NamingEnumeration<SearchResult> objects;
        DirContext ctx = getDirectoryContext();
        
        try 
        {
            objects = ctx.search(ldapGroups, filter, controls);
            return objects.hasMore();
        }
        catch (NamingException e) 
        {
            throw new LdapException("Cannot read LDAP admins group", e);
        }
        finally
        {
        	releaseContext(ctx);
        }
    }

    /**
     * Get a list of all LDAP groups of a specified type.
     * @param gt The type of groups
     * @return List of group names
     * @throws LdapException On any LDAP error
     */
    public Collection<String> getAllGroups(final GroupType gt) throws LdapException {
        return getGroups(getPrefix(gt), null);
    }
    
    public String getHumanName(String groupName, GroupType type) throws LdapException
    {
    	try 
    	{
			return getAttributesOfGroup(groupName, type).get("displayName").get().toString();
		} 
    	catch (NamingException e) 
    	{
			throw new LdapException("Could not retrieve the human name of "+groupName+" with the type "+type,e);
		}
    	catch (NullPointerException e) 
    	{
			throw new LdapException("Could not retrieve the human name of "+groupName+" with the type "+type,e);
		}
    }
    
    public boolean isDeprecated(String groupName, GroupType type) throws LdapException
    {
    	try
    	{
    		//Slightly convolted accessor to handle ldap schemas that might not model this as a boolean
    		return Boolean.valueOf(getAttributesOfGroup(groupName, type).get("deprecated").get().toString());
    	}
    	catch (NamingException e) 
    	{
			throw new LdapException("Could not retrieve the deprecated value of "+groupName+" with the type "+type,e);
		}
    	catch (NullPointerException e) 
    	{
			throw new LdapException("Could not retrieve the deprecated value of "+groupName+" with the type "+type,e);
		}
    }
    
    public String getCategory(String groupName, GroupType type) throws LdapException
    {
    	try 
    	{
			return getAttributesOfGroup(groupName, type).get("category").get().toString();
		} 
    	catch (NamingException e) 
    	{
			throw new LdapException("Could not retrieve the category of "+groupName+" with the type "+type,e);
		}
    	catch (NullPointerException e) 
    	{
			throw new LdapException("Could not retrieve the category of "+groupName+" with the type "+type,e);
		}
    }
    
    public String getDescription(String groupName, GroupType type) throws LdapException
    {
    	try 
    	{
			return getAttributesOfGroup(groupName, type).get("description").get().toString();
		} 
    	catch (NamingException e) 
    	{
			throw new LdapException("Could not retrieve the description of "+groupName+" with the type "+type,e);
		}
    	catch (NullPointerException e) 
    	{
			throw new LdapException("Could not retrieve the description of "+groupName+" with the type "+type,e);
		}
    }
    
    public String getPermissionAuthoritiesAsString(String groupName, GroupType type) throws LdapException
    {
    	try 
    	{
			return getAttributesOfGroup(groupName, type).get("permissionAuthority").get().toString();
		} 
    	catch (NamingException e) 
    	{
			throw new LdapException("Could not retrieve the permission authorities of "+groupName+" with the type "+type,e);
		}
    	catch (NullPointerException e) 
    	{
			throw new LdapException("Could not retrieve the permission authorities of "+groupName+" with the type "+type,e);
		}
    }
    
    private Attributes getAttributesOfGroup(String groupName, GroupType type) throws LdapException
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        String groupPrefix = ldapGroupPrefixOpen;
        
        if (type.equals(GroupType.CLOSED))
        {
     	   groupPrefix = ldapGroupPrefixClosed;
        }
        else if (type.equals(GroupType.ORG))
        {
     	   groupPrefix = ldapGroupPrefixOrg;
        }
        
        String filter = "(&(objectClass=groupOfNames)(cn="+groupPrefix+groupName.toUpperCase()+"))";
        DirContext ctx = getDirectoryContext();
        try 
        {
            NamingEnumeration<SearchResult> objects = ctx.search(ldapGroups, filter, controls);
            SearchResult sr = (SearchResult) objects.next();
            Attributes attributes = sr.getAttributes();
            return attributes;
            
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Failed to retrieve details from LDAP for the group "+groupName, e);
        }
        catch (NullPointerException ex)
        {
            throw new LdapException("Failed to retrieve details from LDAP for the group "+groupName, ex);
        }
        finally
        {
        	releaseContext(ctx);
        }
    }
    	
    /**
     * Get a list of all LDAP groups of a specified type which
     * have been modified since a specified time.
     * @param gt The type of groups
     * @param timestamp The time to compare with
     * @return List of group names
     * @throws LdapException On any LDAP error
     */
    public Collection<String> getModifiedGroups(final GroupType gt, final String timestamp)
            throws LdapException {
        return getGroups(getPrefix(gt), "modifyTimestamp>=" + timestamp);
    }

    /**
     * Get a list of all LDAP users.
     * @return List of user attributes
     * @throws LdapException On any LDAP error
     */
    public Collection<Attributes> getAllUsers() throws LdapException {
        return getUserAttributes(null);
    }

    /**
     * Get a list of all LDAP users which
     * have been modified since a specified time.
     * @param timestamp The time to compare with
     * @return List of user attributes
     * @throws LdapException On any LDAP error
     */
    public Collection<Attributes> getModifiedUsers(final String timestamp)
            throws LdapException {
        return getUserAttributes("modifyTimestamp>=" + timestamp);
    }

    /**
     * Update the timestamp entry and return the time of the
     * previous timestamp (or null if it doesn't exist).
     * @return A timestamp (as a string formatted by the LDAP server) or null
     * @throws LdapException On any LDAP error
     */
    public String updateTimestamp() throws LdapException {
       
    	String oldTime = null;
        DirContext ctx = getDirectoryContext();

        try 
        {
	        // look up time of last timestamp (if it exists)
	        try {
	            String filter = "cn=" + ldapTimestamp;
	            String[] ids = {"modifyTimestamp"};
	            
	            
	            Attributes attributes = ctx.getAttributes(filter, ids);
	
	            Attribute attribute = attributes.get("modifyTimestamp");
	
	            if (attribute != null) {
	                oldTime = (String) attribute.get();
	            }
	        } catch (javax.naming.NamingException e) {
	            oldTime = null;
	        }
	
	        BasicAttribute oc = new BasicAttribute("objectclass");
	        oc.add("top");
	        oc.add("applicationProcess");
	
	        BasicAttributes attributes = new BasicAttributes();
	        attributes.put(oc);
	        attributes.put("cn", ldapTimestamp);
	
	        try {
	        	ctx.rebind("cn=" + ldapTimestamp, null, attributes);
	        } catch (NamingException e) {
	            throw new LdapException("Cannot update timestamp in LDAP", e);
	        }
        }
        finally
        {
        	releaseContext(ctx);
        }

        return oldTime;
    }

    /**
     * Create the LDAP lock entity.
     * @throws LdapException On any LDAP error
     */
    public void createLock() throws LdapException {
        BasicAttribute oc = new BasicAttribute("objectclass");
        oc.add("top");
        oc.add("applicationProcess");

        BasicAttributes attributes = new BasicAttributes();
        attributes.put(oc);
        attributes.put("cn", ldapLock);

        DirContext ctx = getDirectoryContext();
        
        try {
        	ctx.rebind("cn=" + ldapLock, null, attributes);
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Cannot create lock entry in LDAP", e);
        }
        finally {
        	releaseContext(ctx);
        }
    }

    /**
     * Delete the LDAP lock entity.
     * @throws LdapException On any LDAP error
     */
    public void deleteLock() throws LdapException {
    	DirContext ctx = getDirectoryContext();
        try 
        {
        	ctx.unbind("cn=" + ldapLock);
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Cannot delete lock entry in LDAP", e);
        }
        finally
        {
        	releaseContext(ctx);
        }
    }

    /**
     * Does the LDAP lock entity exist?
     * @return True if the lock is present, otherwise false
     * @throws LdapException On any LDAP error
     */
    public boolean lockExists() throws LdapException {
        boolean exists = false;
        DirContext ctx = getDirectoryContext();
        
        try {
            Object obj = ctx.lookup("cn=" + ldapLock);

            if (obj != null) {
                exists = true;
            }
        } 
        catch (javax.naming.NamingException e) 
        {
            exists = false;
        }
        finally
        {
        	releaseContext(ctx);
        }

        return exists;
    }
    
    /**
     * Indicates if ou=groups has been modified since the last update.
     * 
     * @return True if relevant nodes have been modified; false otherwise.
     * @throws LdapException On error connecting to LDAP.
     */
    public boolean isSecurityModelModified() throws LdapException {
    	return SECURITY_MODEL_LAST_UPDATED == null || !securityModel304 || isGroupsModifiedSince(SECURITY_MODEL_LAST_UPDATED);
    }
    
    /**
     * @return The modification timestamp from ou=groups in LDAP.
     * @throws LdapException On error connecting to LDAP.
     */
    public boolean isGroupsModifiedSince(final Date timestamp) throws LdapException {
        String filter = "(modifyTimestamp>=" + LDAP_DATE_FORMAT.format(timestamp) + ")";

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);

        NamingEnumeration<SearchResult> objects;
        DirContext ctx = getDirectoryContext();
        
        try 
        {
            objects = ctx.search(ldapGroups, filter, controls);
            return objects.hasMore();
        }
        catch (NamingException e) 
        {
            throw new LdapException("Cannot read LDAP admins group", e);
        }
        finally
        {
        	releaseContext(ctx);
        }
    }
    
    /**
     * Sets the date and time for when we last updated the security model.
     */
    public void setSecurityModelUpdated() {
    	SECURITY_MODEL_LAST_UPDATED = new Date();
    }

    /**
     * Get a list of the members of a group, searching for the
     * group using an LDAP filter expression and scope.
     * @param filter LDAP search filter (see RFC2254)
     * @param scope One of SearchControls.OBJECT_SCOPE, SearchControls.ONELEVEL_SCOPE,
     * or SearchControls.SUBTREE_SCOPE (see javax.naming.directory.SearchControls)
     * @return List of usernames
     * @throws LdapException On any LDAP error
     */
    private Collection<String> getGroupMembersFromFilter(final String filter, final int scope) throws LdapException {
        Collection<String> memberList = new HashSet<String>(HASH_SIZE);

        SearchControls controls = new SearchControls();
        controls.setSearchScope(scope);

        NamingEnumeration<SearchResult> objects;
        DirContext ctx = getDirectoryContext();
        
        try 
        {
            objects = ctx.search(ldapGroups, filter, controls);

            while (objects.hasMore()) {
                SearchResult sr = (SearchResult) objects.next();
                Attributes attributes = sr.getAttributes();
                Attribute attribute = attributes.get("member");

                if (attribute != null) {
                    NamingEnumeration<?> valueEnum = attribute.getAll();

                    while (valueEnum.hasMore()) {
                        String value = valueEnum.next().toString();

                        final String searchFor = "cn=";
                        int start = value.indexOf(searchFor);
                        int end = value.indexOf(',', start);

                        if (start >= 0 && end >= 0) {
                            String name = value.substring(start + searchFor.length(), end);
                            memberList.add(name);
                        }
                    }
                }
            }
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Failed to retrieve group members from LDAP", e);
        }
        finally
        {
        	releaseContext(ctx);
        }

        return memberList;
    }

    /**
     * Return a list of groups whose names have a specified prefix
     * and which also fulfil a search condition.
     * @param prefix Prefix of groups to search for
     * @param searchCondition LDAP search filter (see RFC2254)
     * @return A list of group names
     * @throws LdapException On any LDAP error
     */
    private Collection<String> getGroups(final String prefix, final String searchCondition)
            throws LdapException {
        Collection<String> groupList = new HashSet<String>(HASH_SIZE);

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        String filter = "cn=" + prefix + "*";

        if (searchCondition != null) {
            filter = "(&(" + searchCondition + ")(" + filter + "))";
        }

        DirContext ctx = getDirectoryContext();
        
        try {
            NamingEnumeration<SearchResult> objects = ctx.search(ldapGroups, filter, controls);

            while (objects.hasMore()) {
                SearchResult sr = (SearchResult) objects.next();
                Attributes attributes = sr.getAttributes();
                Attribute attribute = attributes.get("cn");

                if (attribute != null) {
                    String cn = (String) attribute.get();

                    if (cn != null) {
                        String name = cn.substring(prefix.length());
                        groupList.add(name);
                    }
                }
            }
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Failed to retrieve group list from LDAP", e);
        }
        finally
        {
        	releaseContext(ctx);
        }

        return groupList;
    }

    /**
     * Return a list of users which fulfil an optional search condition.
     * @param searchCondition LDAP search filter (see RFC2254)
     * @return A list of user names
     * @throws LdapException On any LDAP error
     */
    private Collection<Attributes> getUserAttributes(final String searchCondition)
            throws LdapException {
        Collection<Attributes> userList = new HashSet<Attributes>(HASH_SIZE);

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        String filter = "objectClass=" + ldapProfileObjectClass;

        if (searchCondition != null) {
            filter = "(&(" + searchCondition + ")(" + filter + "))";
        }

        DirContext ctx = getDirectoryContext();
        try 
        {
            NamingEnumeration<SearchResult> objects = ctx.search(ldapUsers, filter, controls);

            while (objects.hasMore()) {
                SearchResult sr = (SearchResult) objects.next();
                Attributes attributes = sr.getAttributes();

                if (attributes != null) {
                    userList.add(attributes);
                }
            }
        } 
        catch (NamingException e) 
        {
            throw new LdapException("Failed to retrieve user list from LDAP", e);
        }
        finally
        {
        	releaseContext(ctx);
        }

        return userList;
    }

    /**
     * Get the LDAP group prefix corresponding to a given group type.
     * @param gt The group type
     * @return The prefix
     */
    private String getPrefix(final GroupType gt) {
        String prefix = "";

        switch (gt) {
        case SITE:
            prefix = ldapGroupPrefix;
            break;

        case OPEN:
            prefix = ldapGroupPrefixOpen;
            break;

        case CLOSED:
            prefix = ldapGroupPrefixClosed;
            break;
            
        case ORG:
        	prefix = ldapGroupPrefixOrg;
        	break;
        	
        default:
            break;
        }

        return prefix;
    }

    /**
     * For some reason if you want to modify the object the DN should be returned without the 'dc=...' portion.
     * @param username
     * @return
     * @throws LdapException
     */
    private String getModifiableDN(String username) throws LdapException  {
        String fullDN = getFullDnForUser(username);
        if (fullDN == null)
        {
            return null;
        }
        
        return fullDN.substring(0,fullDN.indexOf(",dc="));
    }    
}

