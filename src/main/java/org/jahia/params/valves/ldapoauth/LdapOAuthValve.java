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
package org.jahia.params.valves.ldapoauth;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.jahia.api.Constants;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthCacheService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.modules.ldapoauthprovider.impl.LdapOAuthProviderImpl;
import org.jahia.params.valves.*;
import org.jahia.pipelines.PipelineException;
import org.jahia.pipelines.valves.ValveContext;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.preferences.user.UserPreferencesHelper;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.Patterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dgaillard
 */
public class LdapOAuthValve extends AutoRegisteredBaseAuthValve {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapOAuthValve.class);
    private static final String VALVE_RESULT = "login_valve_result";
    public static final String PROPERTY_EMAIL = "j:email";
    public static final String PROPERTY_FIRST_NAME = "j:firstName";
    public static final String PROPERTY_LAST_NAME = "j:lastName";
    private JahiaUserManagerService jahiaUserManagerService;
    private JahiaOAuthService jahiaOAuthService;
    private JahiaOAuthCacheService jahiaOAuthCacheService;
    private CookieAuthConfig cookieAuthConfig;
    private LdapOAuthProviderImpl ldapOAuthProviderImpl;
    private String preserveSessionAttributes = null;

    @Override
    public void invoke(Object context, ValveContext valveContext) throws PipelineException {
        final AuthValveContext authContext = (AuthValveContext) context;
        final HttpServletRequest request = authContext.getRequest();

        if (authContext.getSessionFactory().getCurrentUser() != null) {
            valveContext.invokeNext(context);
            return;
        }

        final String originalSessionId = request.getSession().getId();

        final HashMap<String, Object> mapperResult = jahiaOAuthService.getMapperResults(ldapOAuthProviderImpl.getServiceName(), originalSessionId);
        if (mapperResult == null || !request.getParameterMap().containsKey("site")) {
            valveContext.invokeNext(context);
            return;
        }

        boolean ok = false;
        final String siteKey = request.getParameter("site");
        final String userId = (mapperResult.containsKey("j:email")) ? (String) ((Map<String, Object>) mapperResult.get("j:email")).get(JahiaOAuthConstants.PROPERTY_VALUE) : (String) mapperResult.get(JahiaOAuthConstants.CONNECTOR_NAME_AND_ID);
        final JCRUserNode userNode = jahiaUserManagerService.lookupUser(userId, siteKey);

        if (userNode != null) {
            if (!userNode.isAccountLocked()) {
                ok = true;
            } else {
                LOGGER.warn("Login failed: account for user " + userNode.getName() + " is locked.");
                request.setAttribute(VALVE_RESULT, "account_locked");
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Login failed. Unknown username " + userId);
            }
            request.setAttribute(VALVE_RESULT, "unknown_user");
        }

        if (ok) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("User " + userNode + " logged in.");
            }

            // if there are any attributes to conserve between session, let's copy them into a map first
            final Map<String, Object> savedSessionAttributes = preserveSessionAttributes(request);

            final JahiaUser jahiaUser = userNode.getJahiaUser();

            if (request.getSession(false) != null) {
                request.getSession().invalidate();
            }

            if (!originalSessionId.equals(request.getSession().getId())) {
                jahiaOAuthCacheService.updateCacheEntry(originalSessionId, request.getSession().getId());
            }

            // if there were saved session attributes, we restore them here.
            restoreSessionAttributes(request, savedSessionAttributes);

            request.setAttribute(VALVE_RESULT, "ok");
            authContext.getSessionFactory().setCurrentUser(jahiaUser);

            // do a switch to the user's preferred language
            if (SettingsBean.getInstance().isConsiderPreferredLanguageAfterLogin()) {
                final Locale preferredUserLocale = UserPreferencesHelper.getPreferredLocale(userNode, LanguageCodeConverters.resolveLocaleForGuest(request));
                request.getSession().setAttribute(Constants.SESSION_LOCALE, preferredUserLocale);
            }

            final String useCookie = request.getParameter("useCookie");
            if ((useCookie != null) && ("on".equals(useCookie))) {
                // the user has indicated he wants to use cookie authentication
                CookieAuthValveImpl.createAndSendCookie(authContext, userNode, cookieAuthConfig);
            }

            SpringContextSingleton.getInstance().publishEvent(new LdapOAuthValve.LoginEvent(this, jahiaUser, authContext));
        } else {
            valveContext.invokeNext(context);
        }
    }

    private Map<String, Object> preserveSessionAttributes(HttpServletRequest httpServletRequest) {
        final Map<String, Object> savedSessionAttributes = new HashMap<>();
        if ((preserveSessionAttributes != null)
                && (httpServletRequest.getSession(false) != null)
                && (preserveSessionAttributes.length() > 0)) {
            final String[] sessionAttributeNames = Patterns.TRIPLE_HASH.split(preserveSessionAttributes);
            final HttpSession session = httpServletRequest.getSession(false);
            for (String sessionAttributeName : sessionAttributeNames) {
                final Object attributeValue = session.getAttribute(sessionAttributeName);
                if (attributeValue != null) {
                    savedSessionAttributes.put(sessionAttributeName, attributeValue);
                }
            }
        }
        return savedSessionAttributes;
    }

    private void restoreSessionAttributes(HttpServletRequest httpServletRequest, Map<String, Object> savedSessionAttributes) {
        if (savedSessionAttributes.size() > 0) {
            final HttpSession session = httpServletRequest.getSession();
            for (Map.Entry<String, Object> savedSessionAttribute : savedSessionAttributes.entrySet()) {
                session.setAttribute(savedSessionAttribute.getKey(), savedSessionAttribute.getValue());
            }
        }
    }

    public void setJahiaOAuthService(JahiaOAuthService jahiaOAuthService) {
        this.jahiaOAuthService = jahiaOAuthService;
    }

    public void setJahiaOAuthCacheService(JahiaOAuthCacheService jahiaOAuthCacheService) {
        this.jahiaOAuthCacheService = jahiaOAuthCacheService;
    }

    public void setJahiaUserManagerService(JahiaUserManagerService jahiaUserManagerService) {
        this.jahiaUserManagerService = jahiaUserManagerService;
    }

    public void setCookieAuthConfig(CookieAuthConfig cookieAuthConfig) {
        this.cookieAuthConfig = cookieAuthConfig;
    }

    public void setPreserveSessionAttributes(String preserveSessionAttributes) {
        this.preserveSessionAttributes = preserveSessionAttributes;
    }

    public void setLdapOAuthProviderImpl(LdapOAuthProviderImpl ldapOAuthProviderImpl) {
        this.ldapOAuthProviderImpl = ldapOAuthProviderImpl;
    }

    public class LoginEvent extends BaseLoginEvent {

        private static final long serialVersionUID = 8966163034180261958L;

        public LoginEvent(Object source, JahiaUser jahiaUser, AuthValveContext authValveContext) {
            super(source, jahiaUser, authValveContext);
        }
    }
}
