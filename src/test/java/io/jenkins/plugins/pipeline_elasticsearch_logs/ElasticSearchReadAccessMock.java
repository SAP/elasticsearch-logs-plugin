package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.net.URISyntaxException;

import io.jenkins.plugins.pipeline_elasticsearch_logs.read.direct_es.ElasticSearchReadAccessDirect;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.direct_es.ElasticSearchWriteAccessDirect;

/**
 * This class mocks an ElasticSearchAccess and overrides the {@link ElasticSearchWriteAccessDirect#push(String)} method.
 * The data the plugin tries to send to Elasticsearch are collected and can be retrieved via {@link #getEntries()}.
 */
public class ElasticSearchReadAccessMock extends ElasticSearchReadAccessDirect {

    public ElasticSearchReadAccessMock() throws URISyntaxException {
        super();
    }

}
