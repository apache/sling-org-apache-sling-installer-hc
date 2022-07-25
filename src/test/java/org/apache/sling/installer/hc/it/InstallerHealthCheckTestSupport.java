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
package org.apache.sling.installer.hc.it;

import javax.inject.Inject;

import org.apache.sling.testing.paxexam.TestSupport;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;

import static org.apache.sling.testing.paxexam.SlingOptions.awaitility;
import static org.apache.sling.testing.paxexam.SlingOptions.felixHealthcheck;
import static org.apache.sling.testing.paxexam.SlingOptions.hamcrest;
import static org.apache.sling.testing.paxexam.SlingOptions.junit;
import static org.apache.sling.testing.paxexam.SlingOptions.slingInstaller;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.ops4j.pax.exam.CoreOptions.composite;

public abstract class InstallerHealthCheckTestSupport extends TestSupport {

    @Inject
    protected BundleContext bundleContext;

    @Inject
    protected ConfigurationAdmin configurationAdmin;

    protected ModifiableCompositeOption baseConfiguration() {
        return composite(
            super.baseConfiguration(),
            quickstart(),
            // Sling Installer Health Checks
            testBundle("bundle.filename"),
            felixHealthcheck(),
            slingInstaller(),
            // testing
            junit(),
            hamcrest(),
            awaitility()
        );
    }

    protected Option quickstart() {
        final int httpPort = findFreePort();
        final String workingDirectory = workingDirectory();
        return composite(
            slingQuickstartOakTar(workingDirectory, httpPort)
        );
    }

}
