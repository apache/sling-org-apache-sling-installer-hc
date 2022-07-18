/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.sling.installer.hc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.info.InstallationState;
import org.apache.sling.installer.api.info.Resource;
import org.apache.sling.installer.api.info.ResourceGroup;
import org.apache.sling.installer.hc.OsgiInstallerHealthCheck.Configuration;
import org.osgi.framework.Version;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(property={ HealthCheck.NAME+"="+OsgiInstallerHealthCheck.HC_NAME })
@Designate(ocd=Configuration.class)
public class OsgiInstallerHealthCheck implements HealthCheck {
    protected static final String HC_NAME = "OSGi Installer Health Check";

    @Reference
    private InfoProvider infoProvider;

    private static final Logger LOG = LoggerFactory.getLogger(OsgiInstallerHealthCheck.class);

    private Configuration configuration;
    private Map<String, List<Version>> skipEntityIdsWithVersions;
    
    private static final String DOCUMENTATION_URL = "https://sling.apache.org/documentation/bundles/osgi-installer.html#health-check";

    @Reference
    private ConfigurationAdmin configurationAdmin;
    
    @ObjectClassDefinition(name = HC_NAME, 
            description="Checks that all OSGi configurations/bundles are successfully installed by the OSGi Installer (and are not skipped for some reason).")
    protected static @interface Configuration {
        @AttributeDefinition(name="Tags", description="Tags with which this healthcheck is associated")
        @SuppressWarnings("java:S100")
        String[] hc_tags() default {"installer", "osgi"};
        
        @AttributeDefinition(name="URL Prefixes to consider", description = "Only those OSGi configurations/bundles whose location are starting with one of the given URL prefixes are checked (whether they are installed correctly). Open /system/console/osgi-installer for a list of valid prefixes.")
        String[] urlPrefixes() default "jcrinstall:/apps/";
        
        @AttributeDefinition(name="Check Bundles", description = "If enabled bundles are checked (restricted to the ones matching one of the prefixes)")
        boolean checkBundles() default true;
        
        @AttributeDefinition(name="Check Configurations", description = "If enabled configurations are checked (restricted to the ones matching one of the prefixes)")
        boolean checkConfigurations() default true;
        
        @AttributeDefinition(name="Allow ignored artifacts in a group", description = "If true there is no warning reported for not installed artifacts if at least one artifact in the same group (i.e. with the same entity id) is installed matching one of the configured URL prefixes. Otherwise there is a warning for every ignored artifact.")
        boolean allowIgnoredArtifactsInGroup() default false;
        
        @AttributeDefinition(name="Skip entity ids", description = "The given entity ids should be skipped for the health check. Each entry has the format '<entity id> [<version>]'.")
        String[] skipEntityIds();
    }

    @Activate
    protected void activate(Configuration configuration) {
        this.configuration = configuration;
        try {
            skipEntityIdsWithVersions = parseEntityIdsWithVersions(configuration.skipEntityIds());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid configuration in 'skipEntityIds': " + e.getLocalizedMessage(), e);
        }
        
    }

    static Map<String, List<Version>> parseEntityIdsWithVersions(String[] entityIdsAndVersions) throws IllegalArgumentException {
        Map<String, List<Version>> entityIdsWithVersions = new HashMap<>();
        if (entityIdsAndVersions != null) {
            for (String entityIdAndVersion : entityIdsAndVersions) {
                String[] parts = entityIdAndVersion.split(" ", 2);
                final String entityId = parts[0];
                final Version version;
                if (parts.length > 1) {
                    version = Version.parseVersion(parts[1]);
                } else {
                    version = null;
                }
                // does an entry with the same id already exist?
                if (entityIdsWithVersions.containsKey(entityId)) {
                    
                    List<Version> versions = entityIdsWithVersions.get(entityId);
                    // previous entry contained no version?
                    if (versions == null) {
                        throw new IllegalArgumentException("One entry with 'id' " + entityId + " contained no version limitation and there was another entry with the same id. This is an invalid combination. Please only list the same id more than once if different versions are given as well.");
                    }
                    versions.add(version);
                } else {
                    final List<Version> versions;
                    if (version == null) {
                        versions = null;
                    } else {
                        versions = new ArrayList<>(Collections.singletonList(version));
                    }
                    entityIdsWithVersions.put(entityId, versions);
                }
                
            }
        }
        return entityIdsWithVersions;
    }

    @Override
    public Result execute() {
        InstallationState installationState = infoProvider.getInstallationState();
        FormattingResultLog hcLog = new FormattingResultLog();

        int numCheckedConfigurationGroups = 0;
        int numCheckedBundleGroups = 0;
        // go through all resource groups of the OSGi Installer
        for (final ResourceGroup group : installationState.getInstalledResources()) {
            String type = evaluateGroup(group, hcLog);
            switch (type) {
            case InstallableResource.TYPE_CONFIG:
                numCheckedConfigurationGroups++;
                break;
            case InstallableResource.TYPE_BUNDLE:
                numCheckedBundleGroups++;
                break;
            }
        }
        hcLog.info("Checked {} OSGi bundle and {} configuration groups.", numCheckedBundleGroups, numCheckedConfigurationGroups);
        if (hcLog.getAggregateStatus().ordinal() >= Result.Status.WARN.ordinal()) {
            hcLog.info("Refer to the OSGi installer's documentation page at {} for further details on how to fix those issues.", DOCUMENTATION_URL);
        }
        return new Result(hcLog);
    }

    /**
     * @param group
     *            the resource group to evaluate
     * @param hcLog
     *            the log to fill during the health check
     * @return the type of resources in this group ("bundle" or "config") or empty string, if the group was not
     *         considered by this health check
     */
    private String evaluateGroup(ResourceGroup group, FormattingResultLog hcLog) {
        Resource invalidResource = null;
        String resourceType = "";
        boolean isGroupRelevant = false;
        // go through all resources within the given group
        for (Resource resource : group.getResources()) {
            // check for the correct type
            resourceType = resource.getType();
            switch (resourceType) {
            case InstallableResource.TYPE_CONFIG:
                if (!configuration.checkConfigurations()) {
                    LOG.debug("Skip resource '{}', configuration checks are disabled", resource.getEntityId());
                    return "";
                }
                break;
            case InstallableResource.TYPE_BUNDLE:
                if (!configuration.checkBundles()) {
                    LOG.debug("Skip resource '{}', bundle checks are disabled", resource.getEntityId());
                    return "";
                }
                break;
            default:
                LOG.debug("Skip resource '{}' as it is neither a bundle nor a configuration but a {}",
                        resource.getEntityId(), resourceType);
                return "";
            }
            if (Arrays.stream(configuration.urlPrefixes()).anyMatch(resource.getURL()::startsWith)) {
                isGroupRelevant = true;
                switch (resource.getState()) {
                case IGNORED: // means a considered resource was found and it is invalid
                    // still the other resources need to be evaluated
                case INSTALL:
                    if (!configuration.allowIgnoredArtifactsInGroup()) {
                        reportInvalidResource(resource, resourceType, hcLog);
                    } else {
                        if (invalidResource == null) {
                            invalidResource = resource;
                        }
                    }
                    break;
                default:
                    if (configuration.allowIgnoredArtifactsInGroup()) {
                        // means a considered resource was found and it is valid
                        // no need to evaluate other resources from this group
                        return resourceType;
                    }
                }
            } else {
                LOG.debug("Skipping resource '{}' as its URL is not starting with any of these prefixes '{}'", resource, (Object) configuration.urlPrefixes()); // NOSONAR java:S1905
            }
        }
        if (invalidResource != null && configuration.allowIgnoredArtifactsInGroup()) {
            reportInvalidResource(invalidResource, resourceType, hcLog);
        }
        
        // only return resource type if at least one resource in it belonged to a covered url prefix
        return isGroupRelevant ? resourceType : "";
    }

    private void reportInvalidResource(Resource invalidResource, String resourceType, FormattingResultLog hcLog) {
        if (skipEntityIdsWithVersions.containsKey(invalidResource.getEntityId())) {
            List<Version> versions = skipEntityIdsWithVersions.get(invalidResource.getEntityId());
            if (versions != null) {
                for (Version version : versions) {
                    if (version.equals(invalidResource.getVersion())) {
                        LOG.debug("Skipping not installed resource '{}' as its entity id and version is in the skip list", invalidResource);
                        return;
                    }
                }
            } else {
                LOG.debug("Skipping not installed resource '{}' as its entity id is in the skip list", invalidResource);
                return;
            }
        }
        if (resourceType.equals(InstallableResource.TYPE_CONFIG)) {
            hcLog.critical(
                    "The installer state of the OSGi configuration resource '{}' is {}, config might have been manually overwritten!",
                    invalidResource, invalidResource.getState());
        } else {
            hcLog.critical(
                    "The installer state of the OSGi bundle resource '{}' is {}, probably because a later or the same version of that bundle is already installed!",
                    invalidResource, invalidResource.getState());
        }
    }
}
