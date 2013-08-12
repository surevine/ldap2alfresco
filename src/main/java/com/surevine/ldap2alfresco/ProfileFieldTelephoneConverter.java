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
 * Class which can convert a telephone number profile field between different formats.
 */
public class ProfileFieldTelephoneConverter implements ProfileFieldConverter {

    /**
     * Label for the attribute version.
     */
    private String attributeLabel;

    /**
     * Label for the JSON version.
     */
    private String jsonLabel;

    /**
     * Can there be multiple copies of the attribute?
     */
    private boolean allowMultiples;

    /**
     * Logging instance.
     */
    private static final Logger LOGGER = Logger.getLogger(ProfileFieldTelephoneConverter.class);

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


    /**
     * Constructor.
     * @param attributeName Name of attribute
     * @param jsonName Name of JSON encoded attribute
     * @param multiples Can there be multiple attributes with this name?
     */
    ProfileFieldTelephoneConverter(final String attributeName, final String jsonName, final boolean multiples) {
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
                JSONArray jsonNumbers = json.getJSONArray(jsonLabel);

                for (int x = 0; x < jsonNumbers.length(); x++) {
                    JSONObject jsonNumber = jsonNumbers.getJSONObject(x);
                    String number = encodePhoneNumber(jsonNumber);

                    if (number != null && number.length() > 0) {
                        attr.add(number);
                    }
                }
            } else {
                JSONObject jsonNumber = json.getJSONObject(jsonLabel);
                String number = encodePhoneNumber(jsonNumber);

                if (number != null && number.length() > 0) {
                    attr.add(number);
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
                    JSONObject blank = new JSONObject();
                    blank.put(JSON_LABEL_NETWORK, "");
                    blank.put(JSON_LABEL_NUMBER, "");
                    blank.put(JSON_LABEL_EXTENSION, "");
                    json.put(jsonLabel, blank);
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
                    JSONObject entry = decodePhoneNumber(value);

                    if (entry == null) {
                        LOGGER.error("Failed to parse telephone number from :" + value);
                    } else {
                        values.put(entry);
                    }
                }

                json.put(jsonLabel, values);
            } else {
                // expecting only one value
                if (numValues != 1) {
                    LOGGER.error("Expected single value in attribute " + attributeLabel + ", found " + numValues);
                    return;
                }

                String value = attribute.get().toString();
                JSONObject entry = decodePhoneNumber(value);

                if (entry == null) {
                    LOGGER.error("Failed to parse telephone fields from :" + value);
                } else {
                    json.put(jsonLabel, entry);
                }
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
     * Generate comma separated fields in a string representation.
     * @param json JSON encoded phone number
     * @return String encoded version
     */
    private String encodePhoneNumber(final JSONObject json) {
        String network;
        String number;
        String extension;

        try {
            network = json.getString(JSON_LABEL_NETWORK);
            number = json.getString(JSON_LABEL_NUMBER);
            extension = json.getString(JSON_LABEL_EXTENSION);
        } catch (JSONException e) {
            logException(Level.ERROR, e);
            return null;
        }

        if (!isNetworkValid(network)) {
            LOGGER.error("Telephone network is not valid: " + network);
            return null;
        }

        if (!isNumberValid(number)) {
            LOGGER.error("Telephone number is not valid: " + number);
            return null;
        }

        if (!isExtensionValid(extension)) {
            LOGGER.error("Telephone extension is not valid: " + extension);
            return null;
        }

        return network + "," + number + "," + extension;
    }

    /**
     * Separate out comma separated fields in a string representation.
     * @param phone The phone number
     * @return JSON encoded version
     */
    private JSONObject decodePhoneNumber(final String phone) {
        // limit argument of -1 to allow empty strings
        // to be parsed out (otherwise any trailing empty
        // string is removed
        String[] fields = phone.split(",", -1);

        if (fields.length != 3) {
            LOGGER.error("Expected 3 fields for telephone number, found "
                            + fields.length + "fields: " + phone);
            return null;
        }

        String network = fields[0];
        String number = fields[1];
        String extension = fields[2];

        if (!isNetworkValid(network)) {
            LOGGER.error("Telephone network is not valid: " + network);
            return null;
        }

        if (!isNumberValid(number)) {
            LOGGER.error("Telephone number is not valid: " + number);
            return null;
        }

        if (!isExtensionValid(extension)) {
            LOGGER.error("Telephone extension is not valid: " + extension);
            return null;
        }

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
     * Is a string representation of a telephone network valid?
     * @param network Network to check
     * @return if the representation is valid
     */
    private boolean isNetworkValid(final String network) {
        return network.matches("^[A-Z0-9\\- ]+$");
    }

    /**
     * Is a string representation of a telephone number valid?
     * @param number Number to check
     * @return if the representation is valid
     */
    private boolean isNumberValid(final String number) {
        return number.matches("^[0-9 *()+#]+$");
    }

    /**
     * Is a string representation of a telephone extension valid?
     * @param extension Extension to check
     * @return if the representation is valid
     */
    private boolean isExtensionValid(final String extension) {
        return extension.matches("^[0-9 *()+#]*$");
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
