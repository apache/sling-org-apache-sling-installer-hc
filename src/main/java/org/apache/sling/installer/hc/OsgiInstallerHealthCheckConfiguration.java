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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import static org.apache.sling.installer.hc.OsgiInstallerHealthCheck.HC_NAME;

@ObjectClassDefinition(
    name = HC_NAME,
    description = "Checks that all OSGi configurations/bundles are successfully installed by the OSGi Installer (and are not skipped for some reason)."
)
@interface OsgiInstallerHealthCheckConfiguration {

    @AttributeDefinition(
        name = "Tags",
        description = "Tags with which this healthcheck is associated"
    )
    @SuppressWarnings("java:S100")
    String[] hc_tags() default {"installer", "osgi"};

    @AttributeDefinition(
        name = "URL Prefixes to consider",
        description = "Only those OSGi configurations/bundles whose location are starting with one of the given URL prefixes are checked (whether they are installed correctly). Open /system/console/osgi-installer for a list of valid prefixes."
    )
    String[] urlPrefixes() default "jcrinstall:/apps/";

    @AttributeDefinition(
        name = "Check Bundles",
        description = "If enabled bundles are checked (restricted to the ones matching one of the prefixes)"
    )
    boolean checkBundles() default true;

    @AttributeDefinition(
        name = "Check Configurations",
        description = "If enabled configurations are checked (restricted to the ones matching one of the prefixes)"
    )
    boolean checkConfigurations() default true;

    @AttributeDefinition(
        name = "Allow ignored artifacts in a group",
        description = "If true there is no warning reported for not installed artifacts if at least one artifact in the same group (i.e. with the same entity id) is installed matching one of the configured URL prefixes. Otherwise there is a warning for every ignored artifact."
    )
    boolean allowIgnoredArtifactsInGroup() default false;

    @AttributeDefinition(
        name = "Skip entity ids",
        description = "The given entity ids should be skipped for the health check. Each entry has the format '<entity id> [<version>]'."
    )
    String[] skipEntityIds();

}
