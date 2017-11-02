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
package org.jahia.params.valves.ldapoauth.actions;

import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.json.JSONObject;

/**
 * @author dgaillard
 */
public class ManageLdapProviderKeys extends Action {

    private static final String MIXIN_LDAP_OAUTH = "joamix:ldapOAuth";
    public static final String PROPERTY_LDAP_PROVIDER_KEY = "ldapProviderKey";

    @Override
    public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext, Resource resource,
            JCRSessionWrapper session, Map<String, List<String>> parameters,
            URLResolver urlResolver) throws Exception {
        final String action = parameters.get("action").get(0);
        final String connectorServiceName;
        final String mapperServiceName;

        final JSONObject response = new JSONObject();
        if (action.equals("setLdapProviderKey")) {
            connectorServiceName = parameters.get(JahiaOAuthConstants.CONNECTOR_SERVICE_NAME).get(0);
            mapperServiceName = parameters.get(JahiaOAuthConstants.MAPPER_SERVICE_NAME).get(0);

            final String ldapProviderKey = (parameters.containsKey(PROPERTY_LDAP_PROVIDER_KEY)) ? parameters.get(PROPERTY_LDAP_PROVIDER_KEY).get(0) : "";

            final JCRNodeWrapper mappersNode = renderContext.getSite().getNode(JahiaOAuthConstants.JAHIA_OAUTH_NODE_NAME).getNode(connectorServiceName).getNode(JahiaOAuthConstants.MAPPERS_NODE_NAME);

            final JCRNodeWrapper currentMapperNode;
            if (!mappersNode.hasNode(mapperServiceName)) {
                currentMapperNode = mappersNode.addNode(mapperServiceName, parameters.get(JahiaOAuthConstants.NODE_TYPE).get(0));
                currentMapperNode.addMixin(MIXIN_LDAP_OAUTH);
                session.save();
            } else {
                currentMapperNode = mappersNode.getNode(mapperServiceName);
                currentMapperNode.addMixin(MIXIN_LDAP_OAUTH);
                session.save();
            }
            currentMapperNode.setProperty(PROPERTY_LDAP_PROVIDER_KEY, ldapProviderKey);

            session.save();
        } else if (action.equals("getLdapProviderKey")) {
            connectorServiceName = parameters.get(JahiaOAuthConstants.CONNECTOR_SERVICE_NAME).get(0);
            mapperServiceName = parameters.get(JahiaOAuthConstants.MAPPER_SERVICE_NAME).get(0);

            final JCRNodeWrapper mappersNode = renderContext.getSite().getNode(JahiaOAuthConstants.JAHIA_OAUTH_NODE_NAME).getNode(connectorServiceName).getNode(JahiaOAuthConstants.MAPPERS_NODE_NAME);

            if (mappersNode.hasNode(mapperServiceName)) {
                final JCRNodeWrapper currentMapperNode = mappersNode.getNode(mapperServiceName);
                final String ldapProviderKey = currentMapperNode.getPropertyAsString(PROPERTY_LDAP_PROVIDER_KEY);
                response.put(PROPERTY_LDAP_PROVIDER_KEY, ldapProviderKey);
            }
        }
        return new ActionResult(HttpServletResponse.SC_OK, null, response);
    }
}
