package io.jenkins.plugins.pipeline_elasticsearch_logs.testutils;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.JSONUtils.prettyPrint;

import org.junit.Assert;
import org.junit.Test;

/**
 * This test does not test productive code. It only tests the JSONUtils used by
 * other tests.
 */
public class JSONUtilsTest {

    @Test
    public void testPrettyPrint() {
        String before = "{\"c\":3,\"a\":\"1\",\"b\":2}";
        String after = prettyPrint(before);
        String expected = "{\n" + "  \"a\": \"1\",\n" + "  \"b\": 2,\n" + "  \"c\": 3\n" + "}";
        Assert.assertEquals(expected, after);
    }

    @Test
    public void testPrettyPrintDefinedOrder() {
        String before = "{" + "\"predecessors\":1," + "\"displayName\":1," + "\"an_undefined_key\":1,"
                + "\"eventType\":1," + "\"flowNodeId\":1" + "}";
        String after = prettyPrint(before);
        String expected = "{\n" + "  \"eventType\": 1,\n" + "  \"flowNodeId\": 1,\n" + "  \"predecessors\": 1,\n"
                + "  \"displayName\": 1,\n" + "  \"an_undefined_key\": 1\n" + "}";
        Assert.assertEquals(expected, after);
    }

}
