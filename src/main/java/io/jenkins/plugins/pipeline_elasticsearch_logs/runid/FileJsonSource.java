package io.jenkins.plugins.pipeline_elasticsearch_logs.runid;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.io.Files;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class FileJsonSource extends JsonSource {

    private static final Logger LOGGER = Logger.getLogger(FileJsonSource.class.getName());

    private final String jsonFile;

    private transient JSONObject jsonObject;

    @DataBoundConstructor
    public FileJsonSource(String jsonFile) throws IOException, JSONException {
        this.jsonFile = jsonFile;
        this.jsonObject = readFile(this.jsonFile);
    }

    private JSONObject readFile(String filePath) throws IOException {
        File file = new File(filePath);
        String jsonString = Files.toString(file, StandardCharsets.UTF_8);
        return JSONObject.fromObject(jsonString);
    }

    public String getJsonFile() {
        return jsonFile;
    }

    @Override
    public String getJsonString() {
        if (jsonObject == null) getJsonObject();
        if (jsonObject == null) return null;
        return jsonObject.toString();
    }

    @Override
    public JSONObject getJsonObject() {
        try {
            if (jsonObject == null) jsonObject = readFile(jsonFile);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error whild parsing JSONFileSource json file", e);
            return null;
        }
        return jsonObject;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<JsonSource> {

        @Override
        public String getDisplayName() {
            return "JSON file";
        }

        public FormValidation doCheckJsonFile(@QueryParameter("value") String value) {
            return doValidateJSON(value);
        }

        public FormValidation doValidateJSON(@QueryParameter(fixEmpty = true) String jsonFile) {
            String jsonString;
            try {
                File file = new File(jsonFile);
                jsonString = Files.toString(file, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return FormValidation.error(e, "The JSON file could not be read");
            }
            return validateJSONString(jsonString);
        }

    }

}
