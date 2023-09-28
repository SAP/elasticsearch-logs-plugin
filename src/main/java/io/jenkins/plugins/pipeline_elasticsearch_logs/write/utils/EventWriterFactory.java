package io.jenkins.plugins.pipeline_elasticsearch_logs.write.utils;

import javax.annotation.Nonnull;

import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;

/**
 * Functional interface for {@link EventWriter} factories.
 */
public interface EventWriterFactory {
    @Nonnull
    public EventWriter createEventWriter();
}
