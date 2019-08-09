package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import hudson.Extension;
import hudson.model.Queue;

@Extension
public class ElasticSearchFlowExecutionListener extends FlowExecutionListener
{

  private static final Logger LOGGER = Logger.getLogger(ElasticSearchFlowExecutionListener.class.getName());

  @Override
  public void onCreated(FlowExecution execution)
  {

    try
    {

      Queue.Executable exec = execution.getOwner().getExecutable();
      if (exec instanceof WorkflowRun)
      {

        ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration.getRunConfiguration((WorkflowRun) exec);

        if (config == null)
        {
          return;
        }

        ElasticSearchGraphListener graphListener = new ElasticSearchGraphListener(config);
        execution.addListener(graphListener);
      }
    }
    catch (IOException e)
    {
      LOGGER.log(Level.SEVERE, "Failed to get Executable of FlowExecution.");
    }
  }


}
