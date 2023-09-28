package io.jenkins.plugins.pipeline_elasticsearch_logs.write;

import javax.annotation.Nonnull;

import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

/**
 * An event writer config created specifically for a certain Run.
 *
 * In contrast to {@link EventWriterConfig} it must be serializable to be send
 * to remote agents and it must work (be usable) on remote agents.
 */
public interface EventWriterRunConfig extends SerializableOnlyOverRemoting {

    /**
     * Creates a new writer based on this configuration.
     *
     * @return The new writer. Never <code>null</code>.
     */
    @Nonnull
    public abstract EventWriter createEventWriter();
}
