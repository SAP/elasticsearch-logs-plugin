package io.jenkins.plugins.pipeline_elasticsearch_logs.runid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class FileJsonSourceTest {

    @Test
    public void testGetJsonString() throws JSONException, IOException {
        String result = new FileJsonSource(createTestFile("{  \"a\":  \"b\"  }")).getJsonString();
        Assert.assertEquals("{\"a\":\"b\"}", result);
    }

    @Test
    public void testGetJsonObject() throws JSONException, IOException {
        JSONObject result = new FileJsonSource(createTestFile("{  \"a\":  \"b\"  }")).getJsonObject();
        Assert.assertEquals("b", result.getString("a"));
    }

    @Test
    public void testWrongJson() throws IOException {
        try {
            new FileJsonSource(createTestFile("{  \"a\";  \"b\"  }")).getJsonObject();
            Assert.fail("Expected JSONException");
        } catch (JSONException e) {
        }

    }

    @Test
    public void testNonexistingFile() throws JSONException, IOException {
        try {
            new FileJsonSource("/temp/fileDoesNotExist").getJsonObject();
            Assert.fail("Expected FileNotFoundException");
        } catch (FileNotFoundException e) {
        }

    }

    private String createTestFile(String string) throws IOException {
        File tempFile = File.createTempFile(this.getClass().getName(), ".json");
        FileUtils.write(tempFile, string, "UTF-8");
        return tempFile.getAbsolutePath();
    }

}
