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

package jenkins.plugins.publish_over_ssh;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.CopyOnWriteList;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over_ssh.descriptor.BapSshHostConfigurationDescriptor;
import jenkins.plugins.publish_over_ssh.descriptor.BapSshPublisherPluginDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import hudson.util.FormValidation;
import org.kohsuke.stapler.StaplerResponse2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Project-level SSH server configuration. Allows users to define SSH servers directly
 * in their job configuration, overriding or supplementing the global system configuration.
 *
 * <p>Resolution priority: project &gt; folder ancestors &gt; global system config.</p>
 */
public class BapSshSiteJobProperty extends JobProperty<Job<?, ?>> {

    private final CopyOnWriteList<BapSshHostConfiguration> hostConfigurations = new CopyOnWriteList<>();
    private BapSshCommonConfiguration commonConfig;

    @DataBoundConstructor
    public BapSshSiteJobProperty() {
    }

    public BapSshCommonConfiguration getCommonConfig() {
        return commonConfig;
    }

    public void setCommonConfig(final BapSshCommonConfiguration commonConfig) {
        this.commonConfig = commonConfig;
    }

    public List<BapSshHostConfiguration> getHostConfigurations() {
        List<BapSshHostConfiguration> result = new ArrayList<>();
        for (BapSshHostConfiguration hc : hostConfigurations.getView()) {
            result.add(hc);
        }
        Collections.sort(result, Comparator.comparing(BapSshHostConfiguration::getName));
        return result;
    }

    public void setHostConfigurations(final List<BapSshHostConfiguration> configs) {
        BapSshCommonConfiguration effective = effectiveCommonConfig();
        for (BapSshHostConfiguration hc : configs) {
            hc.setCommonConfig(effective);
        }
        hostConfigurations.replaceBy(configs);
    }

    /**
     * Looks up a host configuration by name.
     *
     * @param name the server name
     * @return the matching configuration, or {@code null} if not found
     */
    public BapSshHostConfiguration getConfiguration(final String name) {
        for (BapSshHostConfiguration hc : hostConfigurations) {
            if (hc.getName().equals(name)) {
                return hc;
            }
        }
        return null;
    }

    /**
     * Ensures every host config stored in this property has its {@code commonConfig} resolved.
     * Uses the property's own {@code commonConfig} if present, otherwise falls back to the global one.
     *
     * @param globalCommonConfig the global fallback common configuration
     */
    public void resolveCommonConfig(final BapSshCommonConfiguration globalCommonConfig) {
        BapSshCommonConfiguration effective = (commonConfig != null) ? commonConfig : globalCommonConfig;
        for (BapSshHostConfiguration hc : hostConfigurations) {
            hc.setCommonConfig(effective);
        }
    }

    private BapSshCommonConfiguration effectiveCommonConfig() {
        if (commonConfig != null) {
            return commonConfig;
        }
        BapSshPublisherPluginDescriptor globalDescriptor =
                Jenkins.get().getDescriptorByType(BapSshPublisherPlugin.Descriptor.class);
        return globalDescriptor != null ? globalDescriptor.getCommonConfig() : null;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.siteJobProperty_descriptor_displayName();
        }

        @Override
        public boolean isApplicable(final Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public BapSshSiteJobProperty newInstance(final StaplerRequest2 req, final JSONObject formData) throws FormException {
            if (formData.isNullObject() || formData.isEmpty()) {
                return null;
            }
            BapSshSiteJobProperty property = new BapSshSiteJobProperty();

            if (formData.has("commonConfig")) {
                property.commonConfig = req.bindJSON(BapSshCommonConfiguration.class,
                        formData.getJSONObject("commonConfig"));
            }

            List<BapSshHostConfiguration> newConfigs = req.bindJSONToList(
                    BapSshHostConfiguration.class, formData.get("instance"));
            BapSshCommonConfiguration effective = property.effectiveCommonConfig();
            for (BapSshHostConfiguration hc : newConfigs) {
                hc.setCommonConfig(effective);
            }
            property.hostConfigurations.replaceBy(newConfigs);
            return property;
        }

        public BapSshHostConfigurationDescriptor getHostConfigurationDescriptor() {
            return Jenkins.get().getDescriptorByType(BapSshHostConfigurationDescriptor.class);
        }

        public jenkins.plugins.publish_over.view_defaults.manage_jenkins.Messages getCommonManageMessages() {
            return new jenkins.plugins.publish_over.view_defaults.manage_jenkins.Messages();
        }

        @RequirePOST
        public FormValidation doTestConnection(final StaplerRequest2 request, final StaplerResponse2 response) {
            Job<?, ?> job = request.findAncestorObject(Job.class);
            if (job != null) {
                job.checkPermission(Item.CONFIGURE);
            } else {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            }
            final BapSshHostConfiguration hostConfig = request.bindParameters(BapSshHostConfiguration.class, "");
            hostConfig.setCommonConfig(request.bindParameters(BapSshCommonConfiguration.class, "common."));
            return BapSshPublisherPluginDescriptor.validateConnection(
                    hostConfig, BapSshPublisherPluginDescriptor.createDummyBuildInfo());
        }
    }
}
