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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.surevine.alfresco.AlfrescoException;
import com.surevine.alfresco.PropertyException;
import com.surevine.alfresco.PropertyWrapper;

public class TestRichProfiles {
    
    /**
     * Label for JSON encoding of network field.
     */
    private static final String JSON_LABEL_NETWORK = "network";

    /**
     * Label for JSON encoding of number field.
     */
    private static final String JSON_LABEL_NUMBER = "number";

    /**
     * Label for JSON encoding of extension field.
     */
    private static final String JSON_LABEL_EXTENSION = "extension";
    
    
    private static final Logger LOGGER = Logger.getLogger(TestRichProfiles.class);
    
    
    
    public static void testUpdateToLdap(final String propertiesFilename) {
        
        try {
            PropertyWrapper properties = new PropertyWrapper(propertiesFilename);
            ProfileUpdater profileUpdater = new ProfileUpdater(properties);
            LdapConnector ldap = new LdapConnector(properties);
                
            // richard
            
            JSONArray richardPhoneNumbers = new JSONArray();
            richardPhoneNumbers.put(makePhoneNumber("INTERNAL","123456","9999"));
            richardPhoneNumbers.put(makePhoneNumber("HOME","+44 1234 567890",""));
            richardPhoneNumbers.put(makePhoneNumber("MOBILE","07777 123456",""));
            
            JSONArray richardAskMeAbouts = new JSONArray();
            richardAskMeAbouts.put("cats");
            richardAskMeAbouts.put("juggling");
            richardAskMeAbouts.put("unicycle hockey");
            
            JSONObject richardFields = new JSONObject();
            richardFields.put("telephones", richardPhoneNumbers);
            richardFields.put("askMeAbouts", richardAskMeAbouts);
            richardFields.put("biography", "");
    
            profileUpdater.updateSingleUserToLdap(ldap, "richardl-surevine", richardFields);
                   
            // simon
            
            JSONArray simonPhoneNumbers = new JSONArray();
            simonPhoneNumbers.put(makePhoneNumber("INTERNAL","333","4444"));
            simonPhoneNumbers.put(makePhoneNumber("HOME","+44 2321 1234",""));
            simonPhoneNumbers.put(makePhoneNumber("MOBILE","07799 999999",""));
            
            JSONArray simonAskMeAbouts = new JSONArray();
            simonAskMeAbouts.put("changing nappies");
            simonAskMeAbouts.put("huge laptops");
            
            JSONObject simonFields = new JSONObject();
            simonFields.put("telephones", simonPhoneNumbers);
            simonFields.put("askMeAbouts", simonAskMeAbouts);
            simonFields.put("biography", "I'm Simon");      
            profileUpdater.updateSingleUserToLdap(ldap, "simonw-surevine", simonFields);
            
        } catch (PropertyException e) {
            logException(Level.ERROR, e);
        } catch (LdapException e) {
            logException(Level.ERROR, e);
        } catch (JSONException e) {
            logException(Level.ERROR, e);
        }
    }
    
    private static JSONObject makePhoneNumber(
            final String network,
            final String number,
            final String extension) {
        
        JSONObject json = new JSONObject();

        try {
            json.put(JSON_LABEL_NETWORK, network);
            json.put(JSON_LABEL_NUMBER, number);
            json.put(JSON_LABEL_EXTENSION, extension);
        } catch (JSONException e) {
            logException(Level.ERROR, e);
            return null;
        }
        
        return json;
    }
    
    /**
     * Output an exception's stack trace to the log file.
     * @param level The log level
     * @param e The exception
     */
    private static void logException(final Level level, final Exception e) {
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
