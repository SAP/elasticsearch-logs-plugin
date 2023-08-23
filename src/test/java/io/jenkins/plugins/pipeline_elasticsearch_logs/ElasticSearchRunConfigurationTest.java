package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;

import net.sf.json.JSONObject;

public class ElasticSearchRunConfigurationTest {

    @Test
    public void testGetIndices() throws URISyntaxException {
        ElasticSearchRunConfiguration config = new ElasticSearchRunConfiguration(
                new URI("http://localhost:9200/index1/_doc"),
                null, null, false, null, JSONObject.fromObject("{}"), null, 0, true);
        String[] indices = config.getIndices();
        Assert.assertEquals(1, indices.length);
        Assert.assertEquals("index1", indices[0]);
    }

}
