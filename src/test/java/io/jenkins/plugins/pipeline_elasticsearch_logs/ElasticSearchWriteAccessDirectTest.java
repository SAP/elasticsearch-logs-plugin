package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.write.direct_es.ElasticSearchWriteAccessDirect.CONNECTION_TIMEOUT_DEFAULT;

import java.net.URISyntaxException;

import io.jenkins.plugins.pipeline_elasticsearch_logs.write.direct_es.ElasticSearchWriteAccessDirect;
import org.junit.Assert;
import org.junit.Test;

public class ElasticSearchWriteAccessDirectTest {

    @Test
    public void testGetIndices() throws URISyntaxException {
        ElasticSearchWriteAccessDirect directAccess = new ElasticSearchWriteAccessDirect("http://localhost:9200/index1/_doc", null,null, CONNECTION_TIMEOUT_DEFAULT, null);
        String[] indices = directAccess.getIndices();
        Assert.assertEquals(1, indices.length);
        Assert.assertEquals("index1", indices[0]);
    }

}