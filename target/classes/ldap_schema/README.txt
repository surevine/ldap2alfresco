Installing the new LDAP schema
------------------------------

(modify file paths as appropriate)

Stop OpenLDAP:
    /etc/init.d/ldap stop

Install new schema file:
    cp space.schema /etc/openldap/schema/space.schema 

Edit /etc/openldap/slapd.conf

Add the line "include /etc/openldap/schema/space.schema" after all other "include" lines.

Restart OpenLDAP:
    /etc/init.d/ldap start