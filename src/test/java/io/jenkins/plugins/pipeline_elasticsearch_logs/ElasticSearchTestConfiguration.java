package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.DefaultRunIdProvider;
import net.sf.json.JSONObject;

class ElasticSearchTestConfiguration extends ElasticSearchConfiguration {
    private transient Consumer<Map<String, Object>> receiver;

    ElasticSearchTestConfiguration(Consumer<Map<String, Object>> receiver) throws URISyntaxException {
        super("http://host:123/index/_doc");
        this.receiver = receiver;
        setRunIdProvider(new DefaultRunIdProvider("testInstance"));
    }

    @Override
    protected Supplier<ElasticSearchAccess> getAccessFactory() {
        return () -> new ElasticSearchAccess(null, null, null, CONNECTION_TIMEOUT_DEFAULT) {
            @Override
            public void push(String data) throws IOException {
                Map<String, Object> map = JSONObject.fromObject(data);
                receiver.accept(map);
            }
        };
    }
}
