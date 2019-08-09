package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import net.sf.json.JSONObject;

@Extension
public class ElasticSearchRunListener extends RunListener<Run<?, ?>>
{

  @Override
  public void onFinalized(Run<?, ?> run)
  {
    try
    {
      ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration.getRunConfiguration(run);
      if (config == null)
      {
        return;
      }

      ElasticSearchWriter writer = ElasticSearchWriter.createElasticSearchWriter(config);
      Map<String, Object> data = config.createData();

      data.put("eventType", "buildEnd");
      Result result = run.getResult();
      if (result != null)
      {
        data.put("result", result.toString());
      }
      long duration = run.getDuration();
      if (duration > 0)
      {
        data.put("duration", run.getDuration());
      }
      writer.push(JSONObject.fromObject(data).toString());
    }
    catch (IOException e)
    {
      LOGGER.log(Level.SEVERE, "Failed to get Executable of FlowExecution.");
    }
  }

  private static final Logger LOGGER = Logger.getLogger(ElasticSearchRunListener.class.getName());

  @Override
  public void onInitialize(Run<?, ?> run)
  {

    try
    {
      ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration.getRunConfiguration(run);

      if (config == null)
      {
        return;
      }

      ElasticSearchWriter writer = ElasticSearchWriter.createElasticSearchWriter(config);
      Map<String, Object> data = config.createData();

      data.put("eventType", "buildStart");
      writer.push(JSONObject.fromObject(data).toString());
    }
    catch (IOException e)
    {
      LOGGER.log(Level.SEVERE, "Failed to get Executable of FlowExecution.", e);
    }

  }
}
