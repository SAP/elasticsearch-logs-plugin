package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.Executable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.Queue;
import hudson.model.TaskListener;

@Extension
public class ElasticSearchLogStorageFactory implements LogStorageFactory
{

  private final static Logger LOGGER = Logger.getLogger(ElasticSearchLogStorageFactory.class.getName());

  @Override
  public LogStorage forBuild(FlowExecutionOwner owner)
  {

    try
    {
      Queue.Executable exec = owner.getExecutable();
      if (exec instanceof WorkflowRun)
      {
        ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration.getRunConfiguration((WorkflowRun) exec);
        if (config == null)
        {
          return null;
        }
        WorkflowRun run = (WorkflowRun) exec;
        LOGGER.log(Level.FINER, "Getting LogStorage for: {0}", run.getFullDisplayName());
        return new ElasticSearchLogStorage(config);
      }
      else
      {
        return null;
      }
    }
    catch (IOException x)
    {
      return new BrokenLogStorage(x);
    }
  }
  
  static ElasticSearchLogStorageFactory get()
  {
    return ExtensionList.lookupSingleton(ElasticSearchLogStorageFactory.class);
  }

  private static class ElasticSearchLogStorage implements LogStorage
  {
    private ElasticSearchRunConfiguration config;
    
    ElasticSearchLogStorage(ElasticSearchRunConfiguration config)
    {
      this.config = config;
    }
    
    @Override
    public BuildListener overallListener() throws IOException, InterruptedException
    {
      ElasticSearchSender sender = new ElasticSearchSender(null, config);
      return sender;
    }

    @Override
    public TaskListener nodeListener(FlowNode node) throws IOException, InterruptedException
    {
      NodeInfo nodeInfo = new NodeInfo(node);
      return new ElasticSearchSender(nodeInfo, config);
    }

    @Override
    public AnnotatedLargeText<Executable> overallLog(Executable build, boolean complete)
    {
      ByteBuffer buf = new ByteBuffer();
      return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, true, build);
    }

    @Override
    public AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete)
    {
      ByteBuffer buf = new ByteBuffer();
      return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, true, node);
    }
  }
}
