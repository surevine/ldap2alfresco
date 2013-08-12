/*
 * Copyright (C) 2010 Surevine Ltd.
 *
 * All rights reserved.
 */

package com.surevine.ldap2alfresco;

import javax.naming.directory.Attributes;

import org.json.JSONObject;

/**
 * Interface for classes which can convert profile field between different formats
 */
public interface ProfileFieldConverter {

    // possible future use in profile field editor?
    // Attributes toLdap(JSONObject json);

    /**
     * Encode some attributes as Attributes.
     * @param json The JSON object to convert
     * @param attributes Collection of attributes to insert into
     */
    public void toAttributes(final Attributes attributes, final JSONObject json);
    
    /**
     * Encode some attributes as JSON.
     * @param json The JSON object to insert into
     * @param attributes Collection of attributes
     */
    public void toJson(JSONObject json, final Attributes attributes);
}
