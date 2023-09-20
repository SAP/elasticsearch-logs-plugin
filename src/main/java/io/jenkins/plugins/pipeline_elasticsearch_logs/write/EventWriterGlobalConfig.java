package io.jenkins.plugins.pipeline_elasticsearch_logs.write;

import javax.annotation.Nonnull;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;

/**
 * A global configuration of a certain implementation of {@link EventWriter}.
 */
public abstract class EventWriterGlobalConfig
    extends AbstractDescribableImpl<EventWriterGlobalConfig>
    implements ExtensionPoint {

    public static abstract class EventWriterGlobalConfigDescriptor extends Descriptor<EventWriterGlobalConfig> {
        protected EventWriterGlobalConfigDescriptor() {
        }
    }

    /**
     * Creates a {@link EventWriterRunConfig} based on this configuration.
     *
     * @return The run configuration. Never <code>null</code>.
     */
    @Nonnull
    public abstract EventWriterRunConfig createRunConfig(Run<?, ?> run);
}
