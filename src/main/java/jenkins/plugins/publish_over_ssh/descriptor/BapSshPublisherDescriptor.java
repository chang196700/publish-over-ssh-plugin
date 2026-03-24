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

package jenkins.plugins.publish_over_ssh.descriptor;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Job;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over_ssh.BapSshHostConfiguration;
import jenkins.plugins.publish_over_ssh.BapSshPublisher;
import jenkins.plugins.publish_over_ssh.BapSshPublisherPlugin;
import jenkins.plugins.publish_over_ssh.BapSshSiteJobProperty;
import jenkins.plugins.publish_over_ssh.Messages;
import org.jenkinsci.Symbol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Extension @Symbol("sshPublisherDesc")
public class BapSshPublisherDescriptor extends Descriptor<BapSshPublisher> {

    public BapSshPublisherDescriptor() {
        super(BapSshPublisher.class);
    }

    @Override
    public String getDisplayName() {
        return Messages.publisher_descriptor_displayName();
    }

    public BapSshPublisherPlugin.Descriptor getPublisherPluginDescriptor() {
        return Jenkins.getActiveInstance().getDescriptorByType(BapSshPublisherPlugin.Descriptor.class);
    }

    public BapSshTransferDescriptor getTransferDescriptor() {
        return Jenkins.getActiveInstance().getDescriptorByType(BapSshTransferDescriptor.class);
    }

    /**
     * Returns a merged list of SSH host configurations visible to the given job, combining
     * project-level, folder-ancestor-level, and global configurations.
     *
     * <p>Names are deduplicated: project-level entries take priority over folder-level, which
     * take priority over global entries. The resulting list preserves insertion order so that
     * more-specific configurations appear first in the UI dropdown.</p>
     *
     * @param item the job (or any object — non-Job values are ignored gracefully)
     * @return combined, deduplicated list of host configurations
     */
    public List<BapSshHostConfiguration> getHostConfigurationsForJob(final Object item) {
        Job<?, ?> job = (item instanceof Job) ? (Job<?, ?>) item : null;
        List<BapSshHostConfiguration> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (job != null) {
            // Project-level
            BapSshSiteJobProperty jobProp = job.getProperty(BapSshSiteJobProperty.class);
            if (jobProp != null) {
                addUnique(result, seen, jobProp.getHostConfigurations());
            }
            // Folder ancestors
            addFolderConfigs(result, seen, job.getParent());
        }

        // Global fallback
        addUnique(result, seen, getPublisherPluginDescriptor().getHostConfigurations());
        return result;
    }

    private void addUnique(final List<BapSshHostConfiguration> target,
                           final Set<String> seen,
                           final List<BapSshHostConfiguration> source) {
        for (BapSshHostConfiguration hc : source) {
            if (seen.add(hc.getName())) {
                target.add(hc);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addFolderConfigs(final List<BapSshHostConfiguration> target,
                                  final Set<String> seen,
                                  final ItemGroup<?> parent) {
        if (parent == null || parent instanceof Jenkins) {
            return;
        }
        try {
            if (parent instanceof com.cloudbees.hudson.plugins.folder.AbstractFolder) {
                com.cloudbees.hudson.plugins.folder.AbstractFolder<?> folder =
                        (com.cloudbees.hudson.plugins.folder.AbstractFolder<?>) parent;
                jenkins.plugins.publish_over_ssh.BapSshSiteFolderProperty prop =
                        folder.getProperties().get(jenkins.plugins.publish_over_ssh.BapSshSiteFolderProperty.class);
                if (prop != null) {
                    addUnique(target, seen, prop.getHostConfigurations());
                }
                addFolderConfigs(target, seen, folder.getParent());
            }
        } catch (NoClassDefFoundError ignored) {
            // cloudbees-folder plugin not installed
        }
    }

    public jenkins.plugins.publish_over.view_defaults.BapPublisher.Messages getCommonFieldNames() {
        return new jenkins.plugins.publish_over.view_defaults.BapPublisher.Messages();
    }

}
