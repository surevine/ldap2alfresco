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
