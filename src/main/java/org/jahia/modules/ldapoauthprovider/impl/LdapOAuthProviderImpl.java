/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2017 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.ldapoauthprovider.impl;

import java.util.*;
import javax.jcr.RepositoryException;
import javax.naming.InvalidNameException;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.modules.jahiaoauth.service.MapperService;
import org.jahia.params.valves.ldapoauth.actions.ManageLdapProviderKeys;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.sites.JahiaSitesService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.services.usermanager.ldap.LdapProviderConfiguration;
import org.jahia.services.usermanager.ldap.cache.LDAPCacheManager;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateCallback;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.LdapTemplate;

/**
 * @author dgaillard
 */
public class LdapOAuthProviderImpl implements MapperService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapOAuthProviderImpl.class);
    private JahiaUserManagerService jahiaUserManagerService;
    private JCRTemplate jcrTemplate;
    private List<Map<String, Object>> properties;
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
                    final String userId = (mapperResult.containsKey("j:email")) ? (String) ((Map<String, Object>) mapperResult.get("j:email")).get(JahiaOAuthConstants.PROPERTY_VALUE) : (String) mapperResult.get(JahiaOAuthConstants.CONNECTOR_NAME_AND_ID);

                    final JCRUserNode userNode = jahiaUserManagerService.lookupUser(userId, session);
                    if (userNode == null) {
                        final String siteKey = mapperResult.get(JahiaOAuthConstants.PROPERTY_SITE_KEY).toString();
                        final String connectorServiceName = mapperResult.get(JahiaOAuthConstants.CONNECTOR_SERVICE_NAME).toString();
                        final JCRSiteNode siteNode = JahiaSitesService.getInstance().getSiteByKey(siteKey, session);
                        final JCRNodeWrapper mappersNode = siteNode.getNode(JahiaOAuthConstants.JAHIA_OAUTH_NODE_NAME).getNode(connectorServiceName).getNode(JahiaOAuthConstants.MAPPERS_NODE_NAME);
                        final JCRNodeWrapper currentMapperNode;
                        if (mappersNode.hasNode(serviceName)) {
                            currentMapperNode = mappersNode.getNode(serviceName);

                            final String providerKey = currentMapperNode.getPropertyAsString(ManageLdapProviderKeys.PROPERTY_LDAP_PROVIDER_KEY);

                            try {
                                final LdapProviderConfiguration ldapProviderConfiguration = (LdapProviderConfiguration) SpringContextSingleton.getBean("ldapProviderConfiguration");
                                final LdapTemplateWrapper ldapTemplateWrapper = ldapProviderConfiguration.getLdapTemplateWrapper(providerKey);

                                if (ldapTemplateWrapper == null) {
                                    LOGGER.error("The LDAP provider with key '" + providerKey + "' does not exist");
                                } else {

                                    // Create a container set of attributes
                                    final javax.naming.directory.Attributes container = new javax.naming.directory.BasicAttributes();

                                    // Create the objectclass to add
                                    final javax.naming.directory.Attribute objClasses = new javax.naming.directory.BasicAttribute("objectClass");
                                    objClasses.add("jahia");
                                    objClasses.add("top");
                                    container.put(objClasses);

                                    // Assign the username, first name, and last name
                                    final javax.naming.directory.Attribute uid = new javax.naming.directory.BasicAttribute("uid", userId);
                                    container.put(uid);

                                    final javax.naming.directory.Attribute email = new javax.naming.directory.BasicAttribute("mail", userId);
                                    container.put(email);

                                    if (mapperResult.containsKey("j:firstName")) {
                                        final String firstName = (String) ((Map<String, Object>) mapperResult.get("j:firstName")).get(JahiaOAuthConstants.PROPERTY_VALUE);
                                        if (!firstName.isEmpty()) {
                                            final javax.naming.directory.Attribute commonName = new javax.naming.directory.BasicAttribute("cn", firstName);
                                            container.put(commonName);
                                        }
                                    }

                                    if (mapperResult.containsKey("j:lastName")) {
                                        final String lastName = (String) ((Map<String, Object>) mapperResult.get("j:lastName")).get(JahiaOAuthConstants.PROPERTY_VALUE);
                                        if (!lastName.isEmpty()) {
                                            final javax.naming.directory.Attribute surName = new javax.naming.directory.BasicAttribute("sn", lastName);
                                            container.put(surName);
                                        }
                                    }
                                    final javax.naming.directory.Attribute ssoUser = new javax.naming.directory.BasicAttribute("ssoUser", "false");
                                    container.put(ssoUser);

                                    final javax.naming.ldap.LdapName dn = new javax.naming.ldap.LdapName("ou=jahiaUsers,dc=jahia,dc=com");
                                    dn.add(new javax.naming.ldap.Rdn("uid=" + userId));

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
                                }
                            } catch (InvalidNameException ex) {
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
        this.properties = properties;
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

    private abstract class BaseLdapActionCallback<T> implements LdapTemplateCallback<T> {

        protected BaseLdapActionCallback() {
        }

        @Override
        public void onSuccess() {
        }
    }
}
