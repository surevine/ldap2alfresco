/*
 * Copyright (C) 2010 Surevine Ltd.
 *
 * All rights reserved.
 */

package com.surevine.ldap2alfresco;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Class which can convert a textual profile field between different formats.
 */
public class ProfileFieldTextConverter implements ProfileFieldConverter {

    /**
     * Label for the attribute version.
     */
    private String  attributeLabel;

    /**
     * Label for the JSON version.
     */
    private String  jsonLabel;

    /**
     * Can there be multiple copies of the attribute?
     */
    private boolean allowMultiples;

    /**
     * Maximum string length allowed.
     */
    private static final int MAX_STRING_LENGTH = 1996;

    /**
     * Logging instance.
     */
    private static final Logger LOGGER = Logger.getLogger(ProfileFieldTextConverter.class);


    /**
     * Constructor.
     * @param attributeName Name of attribute
     * @param jsonName Name of JSON encoded attribute
     * @param multiples Can there be multiple attributes with this name?
     */
    ProfileFieldTextConverter(final String attributeName, final String jsonName, final boolean multiples) {
        attributeLabel = attributeName;
        jsonLabel = jsonName;
        allowMultiples = multiples;
    }

    /**
     * Encode some attributes as Attributes.
     * @param json The JSON object to convert
     * @param attributes Collection of attributes to insert into
     */
    public void toAttributes(final Attributes attributes, final JSONObject json) {

        BasicAttribute attr = new BasicAttribute(attributeLabel);

        try {
            if (allowMultiples) {
                JSONArray jsonValues = json.getJSONArray(jsonLabel);

                for (int x = 0; x < jsonValues.length(); x++) {
                    String jsonValue = jsonValues.getString(x);

                    if (jsonValue != null && jsonValue.length() > 0) {
                        attr.add(jsonValue);
                    }
                }
            } else {
                String jsonValue = json.getString(jsonLabel);

                if (jsonValue != null && jsonValue.length() > 0) {
                    attr.add(jsonValue);
                }
            }
        } catch (JSONException e) {
            logException(Level.ERROR, e);
            return;
        }

        attributes.put(attr);
    }

    /**
     * Encode some attributes as JSON.
     * @param json The JSON object to insert into
     * @param attributes Collection of attributes
     */
    public void toJson(final JSONObject json, final Attributes attributes) {

        Attribute attribute = attributes.get(attributeLabel);

        if (attribute == null) {
            LOGGER.debug("Missing attribute: " + attributeLabel);

            // just put an empty entry into the JSON
            try {
                if (allowMultiples) {
                    json.put(jsonLabel, new JSONArray());
                } else {
                    json.put(jsonLabel, "");
                }
            } catch (JSONException e) {
                logException(Level.ERROR, e);
            }

            return;
        }

        int numValues = attribute.size();

        if (numValues == 0) {
            LOGGER.error("Attribute " + attributeLabel + " contains no values");
            return;
        }

        try {
            if (allowMultiples) {

                JSONArray values = new JSONArray();

                NamingEnumeration<?> valueEnum = attribute.getAll();

                while (valueEnum.hasMore()) {
                    String value = valueEnum.next().toString();
                    if (value!=null && value.length() > MAX_STRING_LENGTH)
                    {
                    	value=value.substring(0, MAX_STRING_LENGTH - 1);
                    }
                    values.put(value);
                }

                json.put(jsonLabel, values);
            } else {
                // expecting only one value
                if (numValues != 1) {
                    LOGGER.error("Expected single value in attribute " + attributeLabel + ", found " + numValues);
                    return;
                }

                String value = attribute.get().toString();
                if (value!=null && value.length() > MAX_STRING_LENGTH)
                {
                	value=value.substring(0, MAX_STRING_LENGTH - 1);
                }
                
                json.put(jsonLabel, value);
            }
        } catch (NamingException e) {
            logException(Level.ERROR, e);
            return;
        } catch (JSONException e) {
            logException(Level.ERROR, e);
            return;
        }
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
