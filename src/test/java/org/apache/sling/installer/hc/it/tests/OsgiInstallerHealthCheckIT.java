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
package org.apache.sling.installer.hc.it.tests;

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Objects;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.apache.sling.installer.hc.it.InstallerHealthCheckTestSupport;
import org.apache.sling.installer.hc.it.app.Foo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.sling.testing.paxexam.SlingOptions.slingInstallerFactoryConfiguration;
import static org.apache.sling.testing.paxexam.SlingOptions.slingInstallerProviderJcr;
import static org.awaitility.Awaitility.with;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;
import static org.ops4j.pax.tinybundles.core.TinyBundles.withBnd;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class OsgiInstallerHealthCheckIT extends InstallerHealthCheckTestSupport {

    @Inject
    @Filter(value = "(hc.name=OSGi Installer Health Check)")
    private HealthCheck healthCheck;

    private static final String PID = "org.apache.sling.installer.hc.OsgiInstallerHealthCheck";

    private static final String BAR_PID = "org.apache.sling.installer.hc.it.app.Bar";

    private static final String FOO_PID = "org.apache.sling.installer.hc.it.app.Foo";

    private static final String BSN = "org.apache.sling.installer.hc.it.app";

    @Configuration
    public Option[] configuration() {
        return options(
            baseConfiguration(),
            slingInstallerFactoryConfiguration(),
            slingInstallerProviderJcr(),
            newConfiguration(PID)
                .put("allowIgnoredArtifactsInGroup", true)
                .put("checkBundles", false)
                .put("skipEntityIds", new String[]{"config:org.apache.sling.installer.hc.it.app.Bar 1.0.0"})
                .asOption()
        );
    }

    @ProbeBuilder
    public TestProbeBuilder probeConfiguration(final TestProbeBuilder testProbeBuilder) {
        testProbeBuilder.setHeader("Sling-Initial-Content", "initial-content;ignoreImportProviders:=jar");
        return testProbeBuilder;
    }

    private boolean hasBundle(final String symbolicName, final String version) {
        final Version v = new Version(version);
        final Bundle[] bundles = bundleContext.getBundles();
        for (final Bundle bundle : bundles) {
            if (bundle.getSymbolicName().equals(symbolicName) && bundle.getVersion().equals(v)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConfiguration(final String pid) throws InvalidSyntaxException, IOException {
        final String filter = String.format("(service.pid=%s)", pid);
        org.osgi.service.cm.Configuration[] configurations = configurationAdmin.listConfigurations(filter);
        return !Objects.isNull(configurations) && configurations.length > 0;
    }

    private void installFooBundle() throws BundleException {
        final InputStream inputStream = TinyBundles.
            bundle().
            add(Foo.class).
            set("Bundle-Name", "Apache Sling Installer HealthChecks IT App").
            set("Bundle-SymbolicName", BSN).
            set("Bundle-Version", "1.0.0").
            build(withBnd());
        bundleContext.installBundle(UUID.randomUUID().toString(), inputStream);
    }

    @Test
    public void testHealthCheckExecutionResults() throws Exception {

        with().
            pollInterval(100, MILLISECONDS).
            then().
            await("check for bundle installed via initial content").
            atMost(10, SECONDS).
            until(() -> hasBundle(BSN, "1.0.0"));

        with().
            pollInterval(100, MILLISECONDS).
            then().
            await("check for Bar configuration installed via initial content").
            atMost(10, SECONDS).
            until(() -> hasConfiguration(BAR_PID));

        with().
            pollInterval(100, MILLISECONDS).
            then().
            await("check for Foo configuration installed via initial content").
            atMost(10, SECONDS).
            until(() -> hasConfiguration(FOO_PID));

        {
            final Result result = healthCheck.execute();
            assertThat(result.isOk(), equalTo(true));
            assertThat(result.getStatus(), equalTo(Result.Status.OK));
        }

        {
            final Dictionary<String, Object> properties = new Hashtable<>();
            properties.put("source", "osgi-cm");
            properties.put("updated", true);
            final org.osgi.service.cm.Configuration configuration = configurationAdmin.getConfiguration(FOO_PID, null);
            configuration.update(properties);
        }

        {
            final Dictionary<String, Object> properties = new Hashtable<>();
            properties.put("source", "osgi-cm");
            properties.put("updated", true);
            final org.osgi.service.cm.Configuration configuration = configurationAdmin.getConfiguration(BAR_PID, null);
            configuration.update(properties);
        }

        with().
            pollInterval(100, MILLISECONDS).
            then().
            await().
            atMost(1, SECONDS).
            until(() -> !healthCheck.execute().isOk());
        assertThat(healthCheck.execute().getStatus(), equalTo(Result.Status.CRITICAL));
    }

}
