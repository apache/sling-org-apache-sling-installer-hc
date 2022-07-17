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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.osgi.framework.Version;

import static org.hamcrest.MatcherAssert.assertThat;

public class OsgiInstallerHealthCheckTest {

    @Test
    public void testParseEntityIdsWithVersions() {
        String[] entityIdsAndVersions = new String[] { "idA 1.0.0", "idA 2.0.0", "idB" };
        Map<String, List<Version>> map = OsgiInstallerHealthCheck.parseEntityIdsWithVersions(entityIdsAndVersions);
        assertThat(map, Matchers.allOf(
                Matchers.aMapWithSize(2),
                Matchers.hasEntry("idA", Arrays.asList(new Version("1.0.0"), new Version("2.0.0"))),
                Matchers.hasEntry("idB", null)));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testParseEntityIdsWithVersionsAndConflictingVersions() {
        String[] entityIdsAndVersions = new String[] { "idA", "idA 2.0.0", "idB" };
        OsgiInstallerHealthCheck.parseEntityIdsWithVersions(entityIdsAndVersions);
    }
}
