package io.jenkins.plugins.pipeline_elasticsearch_logs.testutils;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.AbstractList;
import java.util.Vector;

import org.apache.tools.ant.util.StringUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * This class contains methods used to evaluate expected log and Elasticsearch entries.
 */
public class AssertionUtils {

    public final static String ANY = "/.*/";

    public static void assertMatchLines(String expectedLog, String actualLog) {
        Vector<String> expectedLines = StringUtils.lineSplit(expectedLog);
        Vector<String> actualLines = StringUtils.lineSplit(actualLog);
        try {
            assertMatchStringContains(expectedLines, actualLines);
        } catch (AssertionError e) {
            System.out.println(format("EXPECTED LOG:\n%s\nACTUAL LOG:\n%s", expectedLog, actualLog));
            throw e;
        }
    }

    public static void assertMatchEntries(JSONArray expectedLog, AbstractList<String> actualEntries) {
        try {
            assertMatchJSON(expectedLog, actualEntries);
        } catch (AssertionError e) {
            System.out.println(format("EXPECTED LOG:\n%s\nACTUAL LOG:\n%s", expectedLog, String.join("\n", actualEntries)));
            throw e;
        }
    }

    private static void assertMatchStringContains(AbstractList<String> expectedEntries, AbstractList<String> actualEntries) {
        for (int i = 0; i < expectedEntries.size(); i++) {
            String actual = actualEntries.get(i).trim();
            String expected = expectedEntries.get(i).trim();
            assertTrue(String.format("line %s: \"%s\" not contained in \"%s\"", i + 1, expected, actual), actual.contains(expected));
        }
    }

    private static void assertMatchJSON(JSONArray expectedEntries, AbstractList<String> actualEntries) {
        for (int i = 0; i < expectedEntries.size(); i++) {
            assertTrue("actual log does not have a line " + i, actualEntries.size() >= i + 1);
            JSONObject actual = JSONObject.fromObject(actualEntries.get(i).trim());
            JSONObject expected = expectedEntries.getJSONObject(i);
            assertJSONMatch(format("line %s", i), expected, actual);
        }
        assertEquals("Log has more entries than expected", expectedEntries.size(), actualEntries.size());
    }

    private static void assertJSONMatch(String message, JSONObject expected, JSONObject actual) {
        for (Object key : expected.keySet()) {
            Object expectedValue = expected.get(key);
            Object actualValue = actual.get(key);
            try {
                compareJSONValues(String.format("%s: key:'%s'", message, key), expectedValue, actualValue);
            } catch (AssertionError err) {
                err.printStackTrace();
                throw new AssertionError(format("%s: Entry does not match:\nexpected: '%s',\nactual: '%s',\noriginal error: %s", message,
                        expected, actual, err.getMessage()), err);
            }
        }
        // Check additional, unexpected keys
        for (Object key : actual.keySet()) {
            assertTrue(format("%s: Unexpected key '%s' in entry: %s", message, key, actual), expected.containsKey(key));
        }
    }

    private static void compareJSONValues(String message, Object e, Object a) {
        if (e != null && e instanceof String && e.equals(ANY)) {
            return; // any value allowed
        }

        if (e == null ^ a == null)
            assertEquals(message + ": Null compare failed", e == null ? "null" : "non-null", a == null ? "null" : "non-null");
        if (e == null && a == null) return;
        assertEquals(String.format(message + ": Different class. expected: %s, actual: %s", e, a), e.getClass(), a.getClass());
        if (e instanceof JSONObject) {
            assertJSONMatch(message.toString(), (JSONObject) e, (JSONObject) a);
        } else if (e instanceof String) {
            String expectedString = ((String) e).trim();
            if (expectedString.equals(ANY)) {
                return; // any value allowed
            } else if (expectedString.startsWith("/") && expectedString.endsWith("/")) {
                String actualString = ((String) a).trim();
                assertTrue(String.format("%s: Actual String does not match pattern. Pattern: %s, actual: %s", message, expectedString,
                        actualString), actualString.matches(expectedString.substring(1, expectedString.length() - 1)));
            } else {
                assertEquals(message, e, a);
            }
        } else {
            assertEquals(message, e, a);
        }
    }

}
