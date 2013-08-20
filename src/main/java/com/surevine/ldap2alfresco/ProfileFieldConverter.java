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
