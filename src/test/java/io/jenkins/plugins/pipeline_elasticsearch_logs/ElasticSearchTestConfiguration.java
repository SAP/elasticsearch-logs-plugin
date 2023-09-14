package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.Consumer;

import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.DefaultRunIdProvider;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;

class ElasticSearchTestConfiguration extends ElasticSearchConfiguration {
    private transient Consumer<Map<String, Object>> receiver;

    ElasticSearchTestConfiguration(Consumer<Map<String, Object>> receiver) throws URISyntaxException {
        super();
        this.receiver = receiver;
        setRunIdProvider(new DefaultRunIdProvider("testInstance"));
    }

    @Override
    protected SerializableSupplier<ElasticSearchWriteAccess> getWriteAccessFactory() {
        return () -> new ElasticSearchWriteAccess() {
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
        };
    }

}
