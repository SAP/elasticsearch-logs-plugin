package io.jenkins.plugins.pipeline_elasticsearch_logs.write;

import javax.annotation.Nonnull;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;

/**
 * A configuration of a certain implementation of {@link EventWriter} which is
 * part of the plugin's global configuration. This is what users can configure.
 */
public abstract class EventWriterConfig
    extends AbstractDescribableImpl<EventWriterConfig>
    implements ExtensionPoint
{
    public static abstract class EventWriterConfigDescriptor extends Descriptor<EventWriterConfig> {
        protected EventWriterConfigDescriptor() {
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
