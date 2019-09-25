package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
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
public class ElasticSearchLogStorageFactory implements LogStorageFactory {

    private final static Logger LOGGER = Logger.getLogger(ElasticSearchLogStorageFactory.class.getName());

    @Override
    public LogStorage forBuild(FlowExecutionOwner owner) {

        try {
            Queue.Executable exec = owner.getExecutable();
            if (exec instanceof WorkflowRun) {
                ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration
                        .getRunConfiguration((WorkflowRun) exec);
                if (config == null) {
                    return null;
                }
                WorkflowRun run = (WorkflowRun) exec;
                LOGGER.log(Level.FINER, "Getting LogStorage for: {0}", run.getFullDisplayName());
                return new ElasticSearchLogStorage(config);
            } else {
                return null;
            }
        } catch (IOException x) {
            return new BrokenLogStorage(x);
        }
    }

    static ElasticSearchLogStorageFactory get() {
        return ExtensionList.lookupSingleton(ElasticSearchLogStorageFactory.class);
    }

    private static class ElasticSearchLogStorage implements LogStorage {
        private ElasticSearchRunConfiguration config;

        ElasticSearchLogStorage(ElasticSearchRunConfiguration config) {
            this.config = config;
        }

        @Override
        public BuildListener overallListener() throws IOException, InterruptedException {
            return new ElasticSearchSender(null, config);
        }

        @Override
        public TaskListener nodeListener(FlowNode node) throws IOException, InterruptedException {
            NodeInfo nodeInfo = new NodeInfo(node);
            return new ElasticSearchSender(nodeInfo, config);
        }

        @Override
        public AnnotatedLargeText<Executable> overallLog(Executable build, boolean complete) {
            AnnotatedLargeText<Executable> esLog = null;
            if (config.isReadLogsFromElasticsearch())
                esLog = readOverallLogFromElasticsearch(build, complete);
            if (esLog != null && esLog.length() > 0) {
                return esLog;
            }

            AnnotatedLargeText<Executable> fileLog = null;
            fileLog = tryReadLogFile(build, complete);
            if (fileLog != null) {
                return fileLog;
            }

            if (esLog != null) {
                return esLog;
            } else {
                if (config.isReadLogsFromElasticsearch()) {
                    return new BrokenLogStorage(new RuntimeException("Could not get log")).overallLog(build, complete);
                } else {
                    return new AnnotatedLargeText<>(new ByteBuffer(), StandardCharsets.UTF_8, true, build);
                }
            }
        }

        @Override
        public AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete) {
            AnnotatedLargeText<FlowNode> esLog = null;
            if (config.isReadLogsFromElasticsearch())
                esLog = readStepLogFromElasticsearch(node, complete);
            if (esLog != null && esLog.length() > 0) {
                return esLog;
            }

            AnnotatedLargeText<FlowNode> fileLog = null;
            fileLog = tryReadStepFromLogFile(node, complete);
            if (fileLog != null) {
                return fileLog;
            }

            if (esLog != null) {
                return esLog;
            } else {
                if (config.isReadLogsFromElasticsearch()) {
                    return new BrokenLogStorage(new RuntimeException("Could not get log")).stepLog(node, complete);
                } else {
                    return new AnnotatedLargeText<>(new ByteBuffer(), StandardCharsets.UTF_8, true, node);
                }
            }
        }

        private AnnotatedLargeText<Executable> readOverallLogFromElasticsearch(Executable build, boolean complete) {
            try {
                return new ElasticSearchLogReader(config).overallLog(build, complete);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not get overallLog", e);
                return null;
            }
        }

        private AnnotatedLargeText<FlowNode> readStepLogFromElasticsearch(FlowNode node, boolean complete) {
            try {
                return new ElasticSearchLogReader(config).stepLog(node, complete);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not get stepLog", e);
                return null;
            }
        }

        /**
         * Tries to read the log file from the Jenkins master file system. This is the
         * case for logs which were written when the Elasticsearch plugin was
         * deactivated.
         *
         * @param build
         * @param complete
         * @return null if log file not found or extraction failed
         */
        private AnnotatedLargeText<Executable> tryReadLogFile(Executable build, boolean complete) {
            File logFile = getLogFile(build);
            if (logFile == null)
                return null;
            ByteBuffer buf = new ByteBuffer();
            AnnotatedLargeText<Executable> annotatedLargeText = new AnnotatedLargeText<Executable>(buf,
                    StandardCharsets.UTF_8, complete, build);
            try {
                FileUtils.copyFile(logFile, buf);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not copy log file", e);
                return null;
            }
            return annotatedLargeText;
        }

        /**
         * Tries to read the step log file from the Jenkins master file system. This is
         * the case for logs which were written when the Elasticsearch plugin was
         * deactivated.
         *
         * @param node
         * @param complete
         * @return null if log file not found or extraction failed
         */
        private AnnotatedLargeText<FlowNode> tryReadStepFromLogFile(FlowNode node, boolean complete) {
            try {
                hudson.model.Queue.Executable executable = node.getExecution().getOwner().getExecutable();
                if (executable instanceof Executable) {
                    File logFile = getLogFile((Executable) executable);
                    if (logFile == null)
                        return null;
                    if (logFile.length() > 0) {
                        // TODO: extract step log from file (if build log persisted locally and not in
                        // Elasticsearch)
                        return new BrokenLogStorage(new RuntimeException(
                                "Elasticsearch plugin does not provide step log data from local files yet."))
                                        .stepLog(node, complete);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not get step log from log file", e);
            }
            return null;
        }

        /**
         * @param build
         * @return The log file on the master if it exists for this build. Null
         *         otherwise.
         */
        private File getLogFile(Executable build) {
            if (!(build instanceof WorkflowRun))
                return null;
            File logFile = new File(((WorkflowRun) build).getRootDir(), "log");
            if (!logFile.exists())
                return null;
            return logFile;
        }

    }

}
