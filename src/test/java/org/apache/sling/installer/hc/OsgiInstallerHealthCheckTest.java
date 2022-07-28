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
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.info.InstallationState;
import org.apache.sling.installer.api.info.Resource;
import org.apache.sling.installer.api.info.ResourceGroup;
import org.apache.sling.installer.api.tasks.ResourceState;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.osgi.framework.Version;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OsgiInstallerHealthCheckTest {

    private ResourceGroup resourceGroup(final String type, final ResourceState state, final String url, final String entityId) {
        final Resource resource = mock(Resource.class);
        when(resource.getType()).thenReturn(type);
        when(resource.getState()).thenReturn(state);
        when(resource.getURL()).thenReturn(url);
        when(resource.getEntityId()).thenReturn(entityId);
        final List<Resource> resources = new ArrayList<>();
        resources.add(resource);
        final ResourceGroup resourceGroup = mock(ResourceGroup.class);
        when(resourceGroup.getResources()).thenReturn(resources);
        return resourceGroup;
    }

    private OsgiInstallerHealthCheck healthCheck(final OsgiInstallerHealthCheckConfiguration configuration, final List<ResourceGroup> resourceGroups) throws IllegalAccessException {
        final InstallationState installationState = mock(InstallationState.class);
        when(installationState.getInstalledResources()).thenReturn(resourceGroups);
        final InfoProvider infoProvider = mock(InfoProvider.class);
        when(infoProvider.getInstallationState()).thenReturn(installationState);
        final OsgiInstallerHealthCheck healthCheck = new OsgiInstallerHealthCheck();
        FieldUtils.writeDeclaredField(healthCheck, "infoProvider", infoProvider, true);
        healthCheck.configure(configuration);
        return healthCheck;
    }

    @Test
    public void testParseEntityIdsWithVersions() throws IllegalAccessException {
        String[] entityIdsAndVersions = new String[] { "idA 1.0.0", "idA 2.0.0", "idB" };
        final OsgiInstallerHealthCheckConfiguration configuration = mock(OsgiInstallerHealthCheckConfiguration.class);
        when(configuration.skipEntityIds()).thenReturn(entityIdsAndVersions);
        final OsgiInstallerHealthCheck healthCheck = new OsgiInstallerHealthCheck();
        healthCheck.configure(configuration);
        Map<String, List<Version>> map = (Map<String, List<Version>>) FieldUtils.readDeclaredField(healthCheck, "skipEntityIdsWithVersions", true);
        assertThat(map, Matchers.allOf(
                aMapWithSize(2),
                Matchers.hasEntry("idA", Arrays.asList(new Version("1.0.0"), new Version("2.0.0"))),
                Matchers.hasEntry("idB", null)));
    }

    @Test(expected=IllegalStateException.class)
    public void testParseEntityIdsWithVersionsAndConflictingVersions() {
        String[] entityIdsAndVersions = new String[] { "idA", "idA 2.0.0", "idB" };
        final OsgiInstallerHealthCheckConfiguration configuration = mock(OsgiInstallerHealthCheckConfiguration.class);
        when(configuration.skipEntityIds()).thenReturn(entityIdsAndVersions);
        final OsgiInstallerHealthCheck healthCheck = new OsgiInstallerHealthCheck();
        healthCheck.configure(configuration);
    }

    @Test
    public void testParseEmptyEntityIds() throws IllegalAccessException {
        final String[] entityIdsAndVersions = new String[]{};
        final OsgiInstallerHealthCheckConfiguration configuration = mock(OsgiInstallerHealthCheckConfiguration.class);
        when(configuration.skipEntityIds()).thenReturn(entityIdsAndVersions);
        final OsgiInstallerHealthCheck healthCheck = new OsgiInstallerHealthCheck();
        healthCheck.configure(configuration);
        final Map<String, List<Version>> map = (Map<String, List<Version>>) FieldUtils.readDeclaredField(healthCheck, "skipEntityIdsWithVersions", true);
        assertThat(map, aMapWithSize(0));
    }

    @Test
    public void testDisabledConfigurationsCheck() throws IllegalAccessException {
        final OsgiInstallerHealthCheckConfiguration configuration = mock(OsgiInstallerHealthCheckConfiguration.class);
        when(configuration.urlPrefixes()).thenReturn(new String[]{"jcrinstall:/apps/"});
        when(configuration.checkConfigurations()).thenReturn(false);
        when(configuration.checkBundles()).thenReturn(true);

        final List<ResourceGroup> resourceGroups = new ArrayList<>();
        resourceGroups.add(resourceGroup(InstallableResource.TYPE_CONFIG, ResourceState.INSTALL, "jcrinstall:/apps/config/foo.cfg", "config:foo"));
        resourceGroups.add(resourceGroup(InstallableResource.TYPE_BUNDLE, ResourceState.INSTALL, "jcrinstall:/apps/install/foo.jar", "bundle:foo"));
        final HealthCheck healthCheck = healthCheck(configuration, resourceGroups);

        final Result result = healthCheck.execute();
        assertThat(result.isOk(), equalTo(false));
        assertThat(result.getStatus(), equalTo(Result.Status.CRITICAL));
        assertThat(result.toString(), containsString("Checked 1 OSGi bundle and 0 configuration groups."));
    }

    @Test
    public void testDisabledBundlesCheck() throws IllegalAccessException {
        final OsgiInstallerHealthCheckConfiguration configuration = mock(OsgiInstallerHealthCheckConfiguration.class);
        when(configuration.urlPrefixes()).thenReturn(new String[]{"jcrinstall:/apps/"});
        when(configuration.checkConfigurations()).thenReturn(true);
        when(configuration.checkBundles()).thenReturn(false);

        final List<ResourceGroup> resourceGroups = new ArrayList<>();
        resourceGroups.add(resourceGroup(InstallableResource.TYPE_CONFIG, ResourceState.INSTALL, "jcrinstall:/apps/config/foo.cfg", "config:foo"));
        resourceGroups.add(resourceGroup(InstallableResource.TYPE_BUNDLE, ResourceState.INSTALL, "jcrinstall:/apps/install/foo.jar", "bundle:foo"));
        final HealthCheck healthCheck = healthCheck(configuration, resourceGroups);

        final Result result = healthCheck.execute();
        assertThat(result.isOk(), equalTo(false));
        assertThat(result.getStatus(), equalTo(Result.Status.CRITICAL));
        assertThat(result.toString(), containsString("Checked 0 OSGi bundle and 1 configuration groups."));
    }

    @Test
    public void testEmptyUrlPrefixes() throws IllegalAccessException {
        final OsgiInstallerHealthCheckConfiguration configuration = mock(OsgiInstallerHealthCheckConfiguration.class);
        when(configuration.urlPrefixes()).thenReturn(new String[]{});
        when(configuration.checkConfigurations()).thenReturn(true);
        when(configuration.checkBundles()).thenReturn(true);

        final List<ResourceGroup> resourceGroups = new ArrayList<>();
        resourceGroups.add(resourceGroup(InstallableResource.TYPE_CONFIG, ResourceState.INSTALL, "jcrinstall:/apps/config/foo.cfg", "config:foo"));
        final HealthCheck healthCheck = healthCheck(configuration, resourceGroups);

        final Result result = healthCheck.execute();
        assertThat(result.isOk(), equalTo(true));
        assertThat(result.getStatus(), equalTo(Result.Status.OK));
        assertThat(result.toString(), containsString("Checked 0 OSGi bundle and 0 configuration groups."));
    }

}
