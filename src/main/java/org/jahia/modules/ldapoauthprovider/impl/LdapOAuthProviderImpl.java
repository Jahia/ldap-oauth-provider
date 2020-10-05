package org.jahia.modules.ldapoauthprovider.impl;

import org.apache.commons.exec.util.StringUtils;
import org.jahia.modules.jahiaauth.service.*;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.services.usermanager.ldap.JahiaLDAPConfigFactory;
import org.jahia.services.usermanager.ldap.cache.LDAPCacheManager;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateCallback;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.LdapTemplate;

import javax.naming.InvalidNameException;
import javax.naming.directory.Attributes;
import java.util.List;
import java.util.Map;

public class LdapOAuthProviderImpl implements Mapper {

    private static final Logger logger = LoggerFactory.getLogger(LdapOAuthProviderImpl.class);
    private static final String PREFIX_LDAP_O_AUTH_PROPERTIES = "ldapOAuth_";
    private static final String PROPERTY_LDAP_O_AUTH_STATIC_PROPERTIES = PREFIX_LDAP_O_AUTH_PROPERTIES + "static_properties";
    private static final String PROPERTY_LDAP_O_AUTH_USER_BASE_DN = PREFIX_LDAP_O_AUTH_PROPERTIES + "userBaseDn";
    private static final String PROPERTY_OBJECT_CLASS = PREFIX_LDAP_O_AUTH_PROPERTIES + "objectClass";
    private static final String PROPERTY_RDN = PREFIX_LDAP_O_AUTH_PROPERTIES + "rdn";
    private static final String PROPERTY_LDAP_PROVIDER_KEY = PREFIX_LDAP_O_AUTH_PROPERTIES + "providerKey";
    private JahiaUserManagerService jahiaUserManagerService;
    private List<MappedPropertyInfo> properties;
    private LDAPCacheManager ldapCacheManager;
    private String serviceName;

    @Override
    public List<MappedPropertyInfo> getProperties() {
        return properties;
    }

    public void setProperties(List<MappedPropertyInfo> properties) {
        this.properties = properties;
    }

    @Override
    public void executeMapper(final Map<String, MappedProperty> mapperResult, MapperConfig config) {
        final MappedProperty userIdProp = mapperResult.get(JahiaAuthConstants.SSO_LOGIN);
        if (userIdProp == null) {
            return;
        }

        String userId = (String) userIdProp.getValue();
        final JCRUserNode userNode = jahiaUserManagerService.lookupUser(userId);
        if (userNode == null) {
            final String providerKey = config.getProperty(LdapOAuthProviderImpl.PROPERTY_LDAP_PROVIDER_KEY);

            try {
                final JahiaLDAPConfigFactory jahiaLDAPConfigFactory = (JahiaLDAPConfigFactory) BundleUtils.getOsgiService("org.jahia.services.usermanager.ldap.JahiaLDAPConfigFactory", null);
                final LdapTemplateWrapper ldapTemplateWrapper = jahiaLDAPConfigFactory.getLdapTemplateWrapper(providerKey);

                if (ldapTemplateWrapper == null) {
                    logger.error("The LDAP provider with key '{}' does not exist or is not shared", providerKey);
                } else {
                    createUser(config, userId, providerKey, ldapTemplateWrapper, getUserAttributes(mapperResult, config));
                }
            } catch (InvalidNameException | SecurityException | IllegalArgumentException ex) {
                logger.error("An error occurred while adding user {}", userId, ex);
            }
        }
    }

    private Attributes getUserAttributes(Map<String, MappedProperty> mapperResult, MapperConfig config) {
        final String[] objectClasses = StringUtils.split(config.getProperty(PROPERTY_OBJECT_CLASS), ",");
        // Create a container set of attributes
        final Attributes container = new javax.naming.directory.BasicAttributes();

        // Create the objectclass to add
        final javax.naming.directory.Attribute objClasses = new javax.naming.directory.BasicAttribute("objectClass");
        for (String objectClass : objectClasses) {
            objClasses.add(objectClass);
        }
        container.put(objClasses);

        for (MappedPropertyInfo property : properties) {
            if (!property.getName().equals(JahiaAuthConstants.SSO_LOGIN) && mapperResult.containsKey(property.getName())) {
                final String value = (String) mapperResult.get(property.getName()).getValue();
                if (value.isEmpty()) {
                    logger.error("The expected values are not defined");
                } else {
                    container.put(new javax.naming.directory.BasicAttribute(property.getName(), value));
                }
            }
        }

        final String staticProperties = config.getProperty(PROPERTY_LDAP_O_AUTH_STATIC_PROPERTIES);
        if (staticProperties != null && !staticProperties.isEmpty()) {
            for (String staticProperty : staticProperties.split("\n")) {
                final String[] values = staticProperty.split("=");
                final javax.naming.directory.Attribute attribute = new javax.naming.directory.BasicAttribute(values[0], values[1]);
                container.put(attribute);
            }
        }
        return container;
    }

    private void createUser(MapperConfig config, String userId, String providerKey, LdapTemplateWrapper ldapTemplateWrapper, javax.naming.directory.Attributes container) throws InvalidNameException {
        final String userBaseDn = config.getProperty(PROPERTY_LDAP_O_AUTH_USER_BASE_DN);
        final String rdn = config.getProperty(PROPERTY_RDN);

        if (userBaseDn != null && !userBaseDn.isEmpty() && rdn != null && !rdn.isEmpty()) {
            final javax.naming.ldap.LdapName dn = new javax.naming.ldap.LdapName(userBaseDn);
            dn.add(new javax.naming.ldap.Rdn(rdn + userId));

            boolean success = ldapTemplateWrapper.execute(new BaseLdapActionCallback<Boolean>() {

                @Override
                public Boolean doInLdap(LdapTemplate ldapTemplate) {
                    ldapTemplate.bind(dn, null, container);
                    return true;
                }

                @Override
                public Boolean onError(Exception ex) {
                    logger.error("An error occurred while communicating with the LDAP server {}", providerKey, ex);
                    return false;
                }

            });
            if (success) {
                ldapCacheManager.clearUserCacheEntryByName(providerKey, userId);
                jahiaUserManagerService.clearNonExistingUsersCache();

            } else {
                logger.error("An error occurred while adding user {}", userId);
            }
        } else {
            final String errMsg = String.format("Expected userBaseDn and rnd not null/empty, got %s and %s", userBaseDn, rdn);
            logger.error(errMsg);
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setJahiaUserManagerService(JahiaUserManagerService jahiaUserManagerService) {
        this.jahiaUserManagerService = jahiaUserManagerService;
    }

    public void setLdapCacheManager(LDAPCacheManager ldapCacheManager) {
        this.ldapCacheManager = ldapCacheManager;
    }

    private abstract class BaseLdapActionCallback<T> implements LdapTemplateCallback<T> {

        protected BaseLdapActionCallback() {
        }

        @Override
        public void onSuccess() {
        }
    }
}
