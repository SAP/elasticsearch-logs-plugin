package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.pipeline_elasticsearch_logs.utils.RunUtils;

@Extension
public class ElasticsearchFlowExecutionListener extends FlowExecutionListener {

    private static final Logger LOGGER = Logger.getLogger(ElasticsearchFlowExecutionListener.class.getName());

    private final Map<String, ElasticsearchGraphListener> graphListenersByRunId = new HashMap<>();

    @Override
    public void onCreated(FlowExecution execution) {
        try {
            Queue.Executable exec = execution.getOwner().getExecutable();
            if (exec instanceof WorkflowRun) {
                Run<?, ?> run = (Run<?, ?>)exec;
                String runId = RunUtils.getUniqueRunId(run);

                ElasticsearchRunConfig config = ElasticsearchRunConfig.get(run);
                if (config == null) {
                    return;
                }

                ElasticsearchGraphListener graphListener = this.graphListenersByRunId.get(runId);
                if (graphListener == null) {
                    graphListener = new ElasticsearchGraphListener(config);
                    this.graphListenersByRunId.put(runId, graphListener);
                    execution.addListener(graphListener);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to get Executable of FlowExecution.", e);
        }
    }

    @Override
    public void onCompleted(FlowExecution execution) {
        try {
            Queue.Executable exec = execution.getOwner().getExecutable();
            if (exec instanceof Run) {
                Run<?, ?> run = (Run<?, ?>)exec;
                String runId = RunUtils.getUniqueRunId(run);

                ElasticsearchGraphListener graphListener = this.graphListenersByRunId.remove(runId);
                if (graphListener != null) {
                    execution.removeListener(graphListener);
                    try {
                        graphListener.close();
                    }
                    catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, "Failed to close flow graph listener", ex);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to get Executable of FlowExecution.", e);
        }
    }
}
