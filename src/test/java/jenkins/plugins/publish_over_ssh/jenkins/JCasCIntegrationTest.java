/*
 * The MIT License
 *
 * Copyright (C) 2010-2011 by Anthony Robinson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.publish_over_ssh.jenkins;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over_ssh.BapSshCommonConfiguration;
import jenkins.plugins.publish_over_ssh.BapSshHostConfiguration;
import jenkins.plugins.publish_over_ssh.BapSshPublisherPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkinsConfiguredWithCode
class JCasCIntegrationTest {

    @Test
    @ConfiguredWithCode("casc.yaml")
    void testHostConfigurationsLoadedFromJCasC(JenkinsConfiguredWithCodeRule j) {
        BapSshPublisherPlugin.Descriptor descriptor =
                Jenkins.get().getDescriptorByType(BapSshPublisherPlugin.Descriptor.class);
        assertNotNull(descriptor);

        BapSshCommonConfiguration commonConfig = descriptor.getCommonConfig();
        assertNotNull(commonConfig, "commonConfig should be loaded from JCasC");
        assertFalse(commonConfig.isDisableAllExec());

        List<BapSshHostConfiguration> hosts = descriptor.getHostConfigurations();
        assertEquals(2, hosts.size(), "Should have 2 host configurations");

        // hosts are sorted by name; "backup-server" < "my-server"
        BapSshHostConfiguration backupServer = hosts.get(0);
        assertEquals("backup-server", backupServer.getName());
        assertEquals("10.0.0.5", backupServer.getHostname());
        assertEquals("admin", backupServer.getUsername());
        assertEquals("/backup", backupServer.getRemoteRootDir());
        assertEquals(2222, backupServer.getPort());
        assertEquals(60000, backupServer.getTimeout());
        assertTrue(backupServer.isOverrideKey());
        assertEquals("/home/jenkins/.ssh/id_rsa", backupServer.getKeyPath());
        assertTrue(backupServer.isDisableExec());
        assertTrue(backupServer.isAvoidSameFileUploads());

        BapSshHostConfiguration myServer = hosts.get(1);
        assertEquals("my-server", myServer.getName());
        assertEquals("192.168.1.10", myServer.getHostname());
        assertEquals("deploy", myServer.getUsername());
        assertEquals("/var/www", myServer.getRemoteRootDir());
        assertEquals(22, myServer.getPort());
        assertEquals(300000, myServer.getTimeout());
        assertFalse(myServer.isOverrideKey());
        assertFalse(myServer.isDisableExec());
        assertFalse(myServer.isAvoidSameFileUploads());
    }
}
