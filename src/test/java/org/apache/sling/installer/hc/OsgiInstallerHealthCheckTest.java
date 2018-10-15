package org.apache.sling.installer.hc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Version;

public class OsgiInstallerHealthCheckTest {

    @Test
    public void testParseEntityIdsWithVersions() {
        String[] entityIdsAndVersions = new String[] { "idA 1.0.0", "idA 2.0.0", "idB" };
        Map<String, List<Version>> map = OsgiInstallerHealthCheck.parseEntityIdsWithVersions(entityIdsAndVersions);
        Assert.assertThat(map, Matchers.allOf(
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
