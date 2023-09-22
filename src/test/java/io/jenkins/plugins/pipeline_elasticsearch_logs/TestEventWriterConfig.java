package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

import hudson.model.Run;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterConfig;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterRunConfig;

class TestEventWriterConfig extends EventWriterConfig implements EventWriterRunConfig, EventWriter {

    private final Consumer<Map<String, Object>> receiver;

    TestEventWriterConfig(Consumer<Map<String, Object>> receiver) {
        this.receiver = receiver;
    }

    @Override
    public EventWriterRunConfig createRunConfig(Run<?, ?> run) {
        return this;
    }

    @Override
    public EventWriter createEventWriter() {
        return this;
    }

    @Override
    public void push(Map<String, Object> data) throws IOException {
        receiver.accept(data);
    }

    @Override
    public void close() throws IOException {
    }
}
