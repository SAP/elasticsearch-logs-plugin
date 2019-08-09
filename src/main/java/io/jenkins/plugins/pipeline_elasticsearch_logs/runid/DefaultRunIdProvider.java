package io.jenkins.plugins.pipeline_elasticsearch_logs.runid;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchConfiguration;
import net.sf.json.JSONObject;

/**
 * The default runid provider. It uses full project name, build number and instance id.
 *
 */
public class DefaultRunIdProvider extends RunIdProvider
{
  
  private String instanceId;
  
  @DataBoundConstructor
  public DefaultRunIdProvider(String instanceId)
  {
    this.instanceId = instanceId;
  }
  
  public String getInstanceId()
  {
    return instanceId;
  }  

  @Override
  public JSONObject getRunId(Run<?, ?> run)
  {
    JSONObject data = new JSONObject();
    data.element("project", run.getParent().getFullName());
    data.element("build", run.getId());
    data.element("instance", getEffectInstanceId(instanceId));
    return data;
  }
  
  @Extension
  @Symbol("classic")
  public static class DescriptorImpl extends RunIdProviderDescriptor
  {

    @Override
    public String getDisplayName()
    {
      return "Default";
    }
    
  }

}


