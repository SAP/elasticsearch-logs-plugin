package io.jenkins.plugins.pipeline_elasticsearch_logs.runid;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.util.LogTaskListener;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchConfiguration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class JsonRunIdProvider extends RunIdProvider
{

  private static final String JENKINS_INSTANCE_ID_KEY = "JENKINS_INSTANCE_ID";
  private static final String RUN_UID_KEY = "RUN_UID";
  
  private static final Logger LOGGER = Logger.getLogger(JsonRunIdProvider.class.getName());

  private JsonSource jsonSource;

  @DataBoundConstructor
  public JsonRunIdProvider(JsonSource jsonSource)
  {
    this.jsonSource = jsonSource;
  }
  
  public JsonSource getJsonSource()
  {
    return jsonSource;
  }

  @Override
  public JSONObject getRunId(Run<?, ?> run)
  {
    JSONObject jsonObject = jsonSource.getJsonObject();
    expand(jsonObject, getEnvOrEmpty(run));
    return jsonObject;
  }

  private EnvVars getEnvOrEmpty(Run<?, ?> run) {
    EnvVars env = null;
    try {
      env = run.getEnvironment(new LogTaskListener(LOGGER, LOGGER.getLevel()));
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
    if(env == null) env = new EnvVars(); 
    env.put(JENKINS_INSTANCE_ID_KEY, getEffectInstanceId(null));
    env.put(RUN_UID_KEY, ElasticSearchConfiguration.getUniqueRunId(run));
    return env;
  }

  /**
   * Recursively expands all String values based on the provided EnvVars.
   * @param object instanceof JSONObject or JSONArray. Others are be ignored,
   * @param env
   */
  protected void expand(Object object, EnvVars env) {
    if(object instanceof JSONObject) {
      JSONObject jsonObject = ((JSONObject)object);
      Set keys = ((JSONObject)object).keySet();
      for(Object keyObject : keys) {
        if(!(keyObject instanceof String)) continue;
        String key = (String)keyObject;
        Object value = jsonObject.get(key);
        if(value instanceof String) {
          jsonObject.put(key, env.expand((String)value));
        } else {
          expand(value, env);
        }
      }
    } else if(object instanceof JSONArray) {
      JSONArray array = (JSONArray)object;
      for(int i = 0; i < array.size(); i++) {
        Object value = array.get(i);
        if(value instanceof String) {
          array.set(i, env.expand((String)value));
        } else {
          expand(value, env);
        }
      }
    }
  }

  @Extension
  @Symbol("json")
  public static class DescriptorImpl extends RunIdProviderDescriptor
  {
    @Override
    public String getDisplayName()
    {
      return "JSON";
    }
  }

}

