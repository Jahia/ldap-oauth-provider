package org.jahia.modules.ldapoauthprovider.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import javax.jcr.RepositoryException;
import javax.naming.InvalidNameException;
import org.jahia.modules.external.users.UserGroupProviderConfiguration;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.modules.jahiaoauth.service.MapperService;
import org.jahia.params.valves.ldapoauth.LdapOAuthValve;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.sites.JahiaSitesService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.services.usermanager.ldap.cache.LDAPCacheManager;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateCallback;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.LdapTemplate;

public class LdapOAuthProviderImpl implements MapperService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapOAuthProviderImpl.class);
    private static final String KEY_VALUE_NAME = "name";
    private static final String PREFIX_LDAP_O_AUTH_PROPERTIES = "ldapOAuth_";
    private static final String PROPERTY_LDAP_O_AUTH_STATIC_PROPERTIES = PREFIX_LDAP_O_AUTH_PROPERTIES + "static_properties";
    private static final String PROPERTY_LDAP_O_AUTH_USER_BASE_DN = PREFIX_LDAP_O_AUTH_PROPERTIES + "userBaseDn";
    private static final String PROPERTY_OBJECT_CLASS = PREFIX_LDAP_O_AUTH_PROPERTIES + "objectClass";
    private static final String PROPERTY_RDN = PREFIX_LDAP_O_AUTH_PROPERTIES + "rdn";
    private static final String PROPERTY_LDAP_PROVIDER_KEY = PREFIX_LDAP_O_AUTH_PROPERTIES + "providerKey";
    private JahiaUserManagerService jahiaUserManagerService;
    private JCRTemplate jcrTemplate;
    private List<Map<String, Object>> properties;
    private final List<String> expectedProperties = new ArrayList<>();
    private UserGroupProviderConfiguration ldapProviderConfiguration;
    private String serviceName;

    @Override
    public List<Map<String, Object>> getProperties() {
        return properties;
    }

    @Override
    public void executeMapper(final Map<String, Object> mapperResult) {
        try {
            jcrTemplate.doExecuteWithSystemSession(new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    final String userId = (mapperResult.containsKey(LdapOAuthValve.PROPERTY_EMAIL)) ? (String) ((Map<String, Object>) mapperResult.get(LdapOAuthValve.PROPERTY_EMAIL)).get(JahiaOAuthConstants.PROPERTY_VALUE) : (String) mapperResult.get(JahiaOAuthConstants.CONNECTOR_NAME_AND_ID);

                    final JCRUserNode userNode = jahiaUserManagerService.lookupUser(userId, session);
                    if (userNode == null) {
                        final String siteKey = mapperResult.get(JahiaOAuthConstants.PROPERTY_SITE_KEY).toString();
                        final String connectorServiceName = mapperResult.get(JahiaOAuthConstants.CONNECTOR_SERVICE_NAME).toString();
                        final JCRSiteNode siteNode = JahiaSitesService.getInstance().getSiteByKey(siteKey, session);
                        final JCRNodeWrapper mappersNode = siteNode.getNode(JahiaOAuthConstants.JAHIA_OAUTH_NODE_NAME).getNode(connectorServiceName).getNode(JahiaOAuthConstants.MAPPERS_NODE_NAME);
                        if (mappersNode.hasNode(serviceName)) {
                            final String providerKey = siteNode.getPropertyAsString(LdapOAuthProviderImpl.PROPERTY_LDAP_PROVIDER_KEY);

                            try {
                                final Method method = Class.forName("org.jahia.services.usermanager.ldap.LdapProviderConfiguration").getMethod("getLdapTemplateWrapper", String.class);
                                final LdapTemplateWrapper ldapTemplateWrapper = (LdapTemplateWrapper) method.invoke(ldapProviderConfiguration, "providerKey", providerKey);

                                if (ldapTemplateWrapper == null) {
                                    LOGGER.error("The LDAP provider with key '" + providerKey + "' does not exist");
                                } else {
                                    final JCRValueWrapper[] objectClasses = siteNode.getProperty(PROPERTY_OBJECT_CLASS).getValues();
                                    // Create a container set of attributes
                                    final javax.naming.directory.Attributes container = new javax.naming.directory.BasicAttributes();

                                    // Create the objectclass to add
                                    final javax.naming.directory.Attribute objClasses = new javax.naming.directory.BasicAttribute("objectClass");
                                    if (objectClasses == null) {
                                        LOGGER.error("No objectClass has been defined");
                                    } else {
                                        for (JCRValueWrapper objectClass : objectClasses) {
                                            objClasses.add(objectClass.getString());
                                        }
                                    }
                                    container.put(objClasses);

                                    for (String expectedProperty : expectedProperties) {
                                        if (mapperResult.containsKey(expectedProperty)) {
                                            final String value = (String) ((Map<String, Object>) mapperResult.get(expectedProperty)).get(JahiaOAuthConstants.PROPERTY_VALUE);
                                            if (value.isEmpty()) {
                                                LOGGER.error("The expected values are not defined");
                                            } else {
                                                final JCRValueWrapper[] attributes = siteNode.getProperty(PREFIX_LDAP_O_AUTH_PROPERTIES + expectedProperty.replaceAll(":", "_")).getValues();
                                                if (attributes == null) {
                                                    LOGGER.error("The expected properties are not mapped to the LDAP attributes");
                                                } else {
                                                    for (JCRValueWrapper attr : attributes) {
                                                        final javax.naming.directory.Attribute attribute = new javax.naming.directory.BasicAttribute(attr.getString(), value);
                                                        container.put(attribute);
                                                    }
                                                }
                                            }
                                        } else {
                                            final String errMsg = String.format("The expected property %s is not gotten from the OAuth service", expectedProperty);
                                            LOGGER.error(errMsg);
                                        }
                                    }

                                    final String staticProperties = siteNode.getPropertyAsString(PROPERTY_LDAP_O_AUTH_STATIC_PROPERTIES);
                                    if (staticProperties != null && !staticProperties.isEmpty()) {
                                        for (String staticProperty : staticProperties.split("\n")) {
                                            final String[] values = staticProperty.split("=");
                                            final javax.naming.directory.Attribute attribute = new javax.naming.directory.BasicAttribute(values[0], values[1]);
                                            container.put(attribute);
                                        }
                                    }

                                    final String userBaseDn = siteNode.getPropertyAsString(PROPERTY_LDAP_O_AUTH_USER_BASE_DN);
                                    final String rdn = siteNode.getPropertyAsString(PROPERTY_RDN);

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
                                                LOGGER.error("An error occurred while communicating with the LDAP server " + providerKey, ex);
                                                return false;
                                            }

                                        });
                                        if (success) {
                                            final LDAPCacheManager lDAPCacheManager = (LDAPCacheManager) SpringContextSingleton.getBean("ldapCacheManager");
                                            lDAPCacheManager.clearUserCacheEntryByName(providerKey, userId);
                                            jahiaUserManagerService.clearNonExistingUsersCache();

                                        } else {
                                            LOGGER.error("An error occurred while adding user " + userId);
                                        }
                                    } else {
                                        final String errMsg = String.format("Expected userBaseDn and rnd not null/empty, got %s and %s", userBaseDn, rdn);
                                        LOGGER.error(errMsg);
                                    }
                                }
                            } catch (InvalidNameException | ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                                LOGGER.error("An error occurred while adding user " + userId, ex);
                            }
                        }

                    }
                    return null;
                }
            });
        } catch (RepositoryException ex) {
            LOGGER.error("Error executing mapper", ex);
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setProperties(List<Map<String, Object>> properties) {
        this.expectedProperties.clear();
        this.properties = properties;
        for (Map<String, Object> propertiesMap : properties) {
            if (propertiesMap.containsKey(KEY_VALUE_NAME)) {
                expectedProperties.add(propertiesMap.get(KEY_VALUE_NAME).toString());
            }
        }
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setJahiaUserManagerService(JahiaUserManagerService jahiaUserManagerService) {
        this.jahiaUserManagerService = jahiaUserManagerService;
    }

    public void setJcrTemplate(JCRTemplate jcrTemplate) {
        this.jcrTemplate = jcrTemplate;
    }

    public void setLdapProviderConfiguration(UserGroupProviderConfiguration ldapProviderConfiguration) {
        this.ldapProviderConfiguration = ldapProviderConfiguration;
    }

    private abstract class BaseLdapActionCallback<T> implements LdapTemplateCallback<T> {

        protected BaseLdapActionCallback() {
        }

        @Override
        public void onSuccess() {
        }
    }
}
