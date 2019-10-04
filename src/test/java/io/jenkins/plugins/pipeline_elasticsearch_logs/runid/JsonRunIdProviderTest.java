package io.jenkins.plugins.pipeline_elasticsearch_logs.runid;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import hudson.EnvVars;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class JsonRunIdProviderTest {

    @Test
    public void testExpandSimple() throws JSONException, IOException {
        String jsonInput = "{'a':'$b'}";
        EnvVars envInput = new EnvVars("b", "b-from-env");

        JSONObject result = expand(jsonInput, envInput);

        Assert.assertEquals("b-from-env", result.getString("a"));
    }

    @Test
    public void testExpandDeeper() throws JSONException, IOException {
        String jsonInput = "{'a': {'b': {'c': '$d'}}}";
        EnvVars envInput = new EnvVars("d", "d-from-env");

        JSONObject result = expand(jsonInput, envInput);

        Assert.assertEquals("d-from-env", result.getJSONObject("a").getJSONObject("b").getString("c"));
    }

    @Test
    public void testExpandArray() throws JSONException, IOException {
        String jsonInput = "{'a': ['$b', '$c'] }";
        EnvVars envInput = new EnvVars("b", "b-from-env", "c", "c-from-env");

        JSONObject result = expand(jsonInput, envInput);

        Assert.assertEquals("b-from-env", result.getJSONArray("a").getString(0));
        Assert.assertEquals("c-from-env", result.getJSONArray("a").getString(1));
    }

    @Test
    public void testExpandMapInArray() throws JSONException, IOException {
        String jsonInput = "{'a': ['b', {'c': '$d'}]}";
        EnvVars envInput = new EnvVars("d", "d-from-env");

        JSONObject result = expand(jsonInput, envInput);

        Assert.assertEquals("d-from-env", result.getJSONArray("a").getJSONObject(1).getString("c"));
    }

    private JSONObject expand(String jsonInput, EnvVars envInput) {
        JsonRunIdProvider provider = new JsonRunIdProvider(new StringJsonSource(jsonInput.replaceAll("'", "\"")));
        JSONObject jsonObject = provider.getJsonSource().getJsonObject();
        provider.expand(jsonObject, envInput);
        return jsonObject;
    }

}
