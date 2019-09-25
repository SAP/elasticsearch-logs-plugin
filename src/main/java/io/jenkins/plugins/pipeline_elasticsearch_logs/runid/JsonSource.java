package io.jenkins.plugins.pipeline_elasticsearch_logs.runid;

import hudson.model.AbstractDescribableImpl;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;

public abstract class JsonSource extends AbstractDescribableImpl<JsonSource> {

    public abstract String getJsonString();

    public abstract JSONObject getJsonObject();

    protected static FormValidation validateJSONString(String jsonString) {
        try {
            JSONObject.fromObject(jsonString);
        } catch (Exception e) {
            return FormValidation.error(e, "JSON string invalid: " + e.getLocalizedMessage());
        }
        return FormValidation.ok("JSON string OK");
    }

}
