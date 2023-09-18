package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;

class TestWriteAccess extends ElasticSearchWriteAccess {

    private final Consumer<Map<String, Object>> receiver;

    TestWriteAccess(Consumer<Map<String, Object>> receiver) {
        this.receiver = receiver;
    }

    @Override
    public void push(Map<String, Object> data) throws IOException {
        receiver.accept(data);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public SerializableSupplier<ElasticSearchWriteAccess> getSupplier() throws IOException {
        return () -> this;
    }
}
