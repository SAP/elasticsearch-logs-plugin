package io.jenkins.plugins.pipeline_elasticsearch_logs.write;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Nonnull;

public interface EventWriter extends AutoCloseable {

    /**
     * Write a single event.
     *
     * @param data
     *     The single event.
     * @throws IOException
     */
    public void push(@Nonnull Map<String, Object> data) throws IOException;
}
