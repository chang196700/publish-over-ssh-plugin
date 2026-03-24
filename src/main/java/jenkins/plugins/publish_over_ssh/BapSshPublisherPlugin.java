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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.ItemGroup;
import hudson.model.Job;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over.BPPlugin;
import jenkins.plugins.publish_over.BPPluginDescriptor;
import jenkins.plugins.publish_over_ssh.descriptor.BapSshPublisherPluginDescriptor;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;


@SuppressWarnings({ "PMD.TooManyMethods", "PMD.LooseCoupling" })
public class BapSshPublisherPlugin extends BPPlugin<BapSshPublisher, BapSshClient, BapSshCommonConfiguration> {

    private static final long serialVersionUID = 1L;

    /** Holds the current build during {@link #perform} to allow context-aware config lookup. */
    static final ThreadLocal<AbstractBuild<?, ?>> CURRENT_BUILD = new ThreadLocal<>();

    public BapSshPublisherPlugin(final ArrayList<BapSshPublisher> publishers, final boolean continueOnError, final boolean failOnError,
                                 final boolean alwaysPublishFromMaster, final String masterNodeName,
                                 final BapSshParamPublish paramPublish) {
        super(Messages.console_message_prefix(), publishers, continueOnError, failOnError, alwaysPublishFromMaster, masterNodeName,
                paramPublish);
    }

    @DataBoundConstructor
    public BapSshPublisherPlugin() {
        super(Messages.console_message_prefix());
    }

    public BapSshParamPublish getParamPublish() {
        return (BapSshParamPublish) getDelegate().getParamPublish();
    }

    @DataBoundSetter
    public void setParamPublish(final BapSshParamPublish paramPublish) {
        this.getDelegate().setParamPublish(paramPublish);
    }

    public List<BapSshPublisher> getPublishers() {
        return this.getDelegate().getPublishers();
    }

    @DataBoundSetter
    public void setPublishers(final ArrayList<BapSshPublisher> publishers) {
        this.getDelegate().setPublishers(publishers);
    }

    public boolean isContinueOnError() {
        return this.getDelegate().isContinueOnError();
    }

    @DataBoundSetter
    public void setContinueOnError(final boolean continueOnError) {
        this.getDelegate().setContinueOnError(continueOnError);
    }

    public boolean isFailOnError() {
        return this.getDelegate().isFailOnError();
    }

    @DataBoundSetter
    public void setFailOnError(final boolean failOnError) {
        this.getDelegate().setFailOnError(failOnError);
    }

    public boolean isAlwaysPublishFromMaster() {
        return this.getDelegate().isAlwaysPublishFromMaster();
    }

    @DataBoundSetter
    public void setAlwaysPublishFromMaster(final boolean alwaysPublishFromMaster) {
        this.getDelegate().setAlwaysPublishFromMaster(alwaysPublishFromMaster);
    }

    public String getMasterNodeName() {
        return this.getDelegate().getMasterNodeName();
    }

    @DataBoundSetter
    public void setMasterNodeName(final String masterNodeName) {
        this.getDelegate().setMasterNodeName(masterNodeName);
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        return addToEquals(new EqualsBuilder(), (BapSshPublisherPlugin) that).isEquals();
    }

    @Override
    public int hashCode() {
        return addToHashCode(new HashCodeBuilder()).toHashCode();
    }

    @Override
    public String toString() {
        return addToToString(new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)).toString();
    }

    @Override
    public Descriptor getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(Descriptor.class);
    }

    /**
     * Overrides {@code perform} to capture the current build in a thread-local so that
     * {@link #getConfiguration(String)} can resolve servers from project/folder properties.
     */
    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {
        CURRENT_BUILD.set(build);
        try {
            return super.perform(build, launcher, listener);
        } finally {
            CURRENT_BUILD.remove();
        }
    }

    /**
     * Resolves the named SSH server configuration by searching the project and its ancestor
     * folders before falling back to the global system configuration.
     *
     * <p>Priority: project-level &gt; folder ancestors (nearest first) &gt; global.</p>
     */
    @Override
    public BapSshHostConfiguration getConfiguration(final String name) {
        AbstractBuild<?, ?> build = CURRENT_BUILD.get();
        if (build != null) {
            Job<?, ?> job = build.getProject();
            // Check job-level property first
            BapSshSiteJobProperty jobProp = job.getProperty(BapSshSiteJobProperty.class);
            if (jobProp != null) {
                jobProp.resolveCommonConfig(getDescriptor().getCommonConfig());
                BapSshHostConfiguration config = jobProp.getConfiguration(name);
                if (config != null) {
                    return config;
                }
            }
            // Walk up the folder hierarchy
            BapSshHostConfiguration config = resolveFromFolderHierarchy(job.getParent(), name);
            if (config != null) {
                return config;
            }
        }
        return getDescriptor().getConfiguration(name);
    }

    private BapSshHostConfiguration resolveFromFolderHierarchy(final ItemGroup<?> parent, final String name) {
        if (parent == null) {
            return null;
        }
        // Guarded so the plugin works without the cloudbees-folder plugin installed.
        try {
            if (parent instanceof com.cloudbees.hudson.plugins.folder.AbstractFolder) {
                com.cloudbees.hudson.plugins.folder.AbstractFolder<?> folder =
                        (com.cloudbees.hudson.plugins.folder.AbstractFolder<?>) parent;
                BapSshSiteFolderProperty prop = folder.getProperties().get(BapSshSiteFolderProperty.class);
                if (prop != null) {
                    prop.resolveCommonConfig(getDescriptor().getCommonConfig());
                    BapSshHostConfiguration config = prop.getConfiguration(name);
                    if (config != null) {
                        return config;
                    }
                }
                return resolveFromFolderHierarchy(folder.getParent(), name);
            }
        } catch (NoClassDefFoundError ignored) {
            // cloudbees-folder plugin not installed — skip folder-level lookup
        }
        return null;
    }

    @Extension @Symbol("sshPublisher")
    public static class Descriptor extends BapSshPublisherPluginDescriptor {
        @Override
        public Object readResolve() {
            return super.readResolve();
        }
    }

    /**
     * @deprecated
     * prevent xstream noise
     * */
    @Deprecated
    public static class DescriptorMessages implements BPPluginDescriptor.BPDescriptorMessages { }

}
