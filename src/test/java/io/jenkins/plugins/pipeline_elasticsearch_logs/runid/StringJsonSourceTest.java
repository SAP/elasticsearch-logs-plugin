package io.jenkins.plugins.pipeline_elasticsearch_logs.runid;

import org.junit.Assert;
import org.junit.Test;

import net.sf.json.JSONObject;

public class StringJsonSourceTest
{

    @Test
    public void testGetJsonString() {
        String result = new StringJsonSource("{  \"a\":  \"b\"  }").getJsonString();
        Assert.assertEquals("{\"a\":\"b\"}", result);
    }

    @Test
    public void testGetJsonObject() {
        JSONObject result = new StringJsonSource("{  \"a\":  \"b\"  }").getJsonObject();
        Assert.assertEquals("b", result.getString("a"));
    }

}
