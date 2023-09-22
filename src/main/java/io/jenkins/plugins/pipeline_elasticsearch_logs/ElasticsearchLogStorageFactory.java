package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Queue;

@Extension
public class ElasticsearchLogStorageFactory implements LogStorageFactory {

    private final static Logger LOGGER = Logger.getLogger(ElasticsearchLogStorageFactory.class.getName());

    @Override
    public LogStorage forBuild(FlowExecutionOwner owner) {

        try {
            Queue.Executable exec = owner.getExecutable();
            if (exec instanceof WorkflowRun) {
                ElasticsearchRunConfig config = ElasticsearchRunConfig.get((WorkflowRun) exec);
                if (config == null) {
                    return null;
                }
                WorkflowRun run = (WorkflowRun) exec;
                LOGGER.log(Level.FINER, "Getting LogStorage for: {0}", run.getFullDisplayName());
                return ElasticsearchLogStorage.forFile(config, new File(owner.getRootDir(), "log"));
            } else {
                return null;
            }
        } catch (IOException x) {
            return new BrokenLogStorage(x);
        }
    }

    static ElasticsearchLogStorageFactory get() {
        return ExtensionList.lookupSingleton(ElasticsearchLogStorageFactory.class);
    }

}
