package io.jenkins.plugins.pipeline_elasticsearch_logs.testutils;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.AssertionUtils.ANY;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Helper methods for JSON handling.
 */
public class JSONUtils {

    // That's the order we want the keys to have for
    // better readability of the test log resources.
    static final String[] PRETTYPRINT_KEY_ORDER = new String[] { "eventType", "step", "flowNodeId", "startId", "predecessors", "message",
            "displayName", "result", "errorMessage", "stageId", "stageName", "duration", "agent", "annotations", "timestamp",
            "timestampMillis", "runId", "uid" };

    @SuppressWarnings("serial")
    static final Map<String, String> DEFAULT_REPLACE_MAP = new HashMap<String, String>() {
        {
            put("duration", ANY);
            put("runId", ANY);
            put("timestampMillis", ANY);
            put("timestamp", ANY);
            put("uid", ANY);
            put("annotations", ANY);
        }
    };

    public static String prettyPrint(String jsonString) {
        JSONObject json = JSONObject.fromObject(jsonString);
        json = sortKeys(json);
        return json.toString(2);
    }

    public static String prettyPrint(JSONObject json) {
        json = sortKeys(json);
        return json.toString(2);
    }

    /**
     * This method replaces in the json object the values of all keys in the replaceKeyValue
     * map with the values from there. This method can be used to generate test content e.g. with
     * regex values accepting ANY value for all timestamps.
     *
     * @param json
     * @param replaceKeyValue
     */
    public static void replaceValues(JSONObject json, Map<String, String> replaceKeyValue) {
        @SuppressWarnings("unchecked")
        Set<String> keySet = json.keySet();
        for (String key : keySet) {
            String value = replaceKeyValue.get(key);
            if (value != null) {
                json.replace(key, value);
            }
        }
    }

    /**
     * This method can be used to generate test data. It writes test content into the targetFile.
     * The data is taken from the mockWriterData list. The entries are processed by
     * sorting the keys and replacing some values.<br/>
     * <br/>
     * Example usage:
     * <ul>
     * <li>debug break at the end of a test (like IntegrationTest.testPipelineWithElasticsearchPlugin)</li>
     * <li>Execute in the Display:<br/>
     * <code>io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.JSONUtils.writeTestResourceContent(
     *     new java.io.File("testResource.json"), mockWriter.getEntries())</code></li>
     * </ul>
     *
     * @param targetFile
     * @param mockWriterData
     *            list with json strings per log entry
     * @throws IOException
     */
    public static void writeTestResourceContent(File targetFile, ArrayList<String> mockWriterData) throws IOException {
        JSONArray jsonArray = new JSONArray();
        for (String line : mockWriterData) {
            JSONObject jsonLine = JSONObject.fromObject(line);
            jsonLine = sortKeys(jsonLine);
            replaceValues(jsonLine, DEFAULT_REPLACE_MAP);
            jsonArray.add(jsonLine);
        }
        System.out.println("Writing test resource: " + targetFile.getAbsolutePath());
        FileUtils.write(targetFile, jsonArray.toString(2), "UTF-8");
    }

    /**
     * Sorts the first level keys of the json object according to
     * the order defined in PRETTYPRINT_KEY_ORDER.
     *
     * @param json
     * @return
     */
    private static JSONObject sortKeys(JSONObject json) {
        @SuppressWarnings("unchecked")
        TreeSet<String> keys = new TreeSet<String>(json.keySet());
        JSONObject newJson = new JSONObject();
        // Process defined keys
        for (String key : PRETTYPRINT_KEY_ORDER) {
            if (keys.remove(key)) {
                newJson.put(key, json.get(key));
            }
        }
        // Process undefined keys
        for (String key : keys) {
            System.out.println("Not yet defined sort key: " + key);
            newJson.put(key, json.get(key));
        }
        return newJson;
    }

}
