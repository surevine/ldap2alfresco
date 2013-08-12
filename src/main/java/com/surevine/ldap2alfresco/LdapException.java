/*
 * Copyright (C) 2010 Surevine Ltd.
 *
 * All rights reserved.
 */

package com.surevine.ldap2alfresco;

/**
 * Class for exceptions thrown by LdapConnector.
 */
public class LdapException extends Exception {
    /**
     * Required for serialisable classes.
     */
    private static final long serialVersionUID = 7343363909806392390L;

    /**
     * Construct with a message.
     * @param message The message
     */
    public LdapException(final String message) {
        super(message);
    }

    /**
     * Construct from another exception which caused the problem.
     * @param message The message
     * @param cause The exception that caused this
     */
    public LdapException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
