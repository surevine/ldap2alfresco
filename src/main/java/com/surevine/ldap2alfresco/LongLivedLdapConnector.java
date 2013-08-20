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

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import com.surevine.alfresco.PropertyWrapper;

/**
 * A version of LdapConnector designed for longer-lived, which is to say non-batch, processing.
 * Instead of reusing the same connection to LDAP, this class re-establishes a new connection
 * every time it is called for.
 * 
 * This is slower, but much more fault tolerant in an environment where an instance of this class might
 * survive for days at a time
 * @author simonw
 *
 */
public class LongLivedLdapConnector extends LdapConnector {
	
	public LongLivedLdapConnector(final PropertyWrapper properties) throws LdapException {
		super(properties);
	}
	
	@Override
    protected DirContext getDirectoryContext() throws LdapException
    {
   		// obtain initial directory context using the environment
   		try 
   		{
    		return new InitialDirContext(ldapEnv);
    	} 
   		catch (NamingException e)
    	{
    		throw new LdapException("Cannot connect to LDAP server", e);
    	}
    }
	
	@Override
	protected void releaseContext(DirContext ctx)
	{
		if (ctx!=null)
		{
			try 
			{
				ctx.close();
			}
			catch (Exception e)
			{
				//If we get a problem closing the connection there's nothing we can do, so silently ignore
			}
		}
	}
	
}
