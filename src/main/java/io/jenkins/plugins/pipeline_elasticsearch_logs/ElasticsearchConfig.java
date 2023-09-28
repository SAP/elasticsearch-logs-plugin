package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.net.URISyntaxException;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.DefaultRunIdProvider;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.RunIdProvider;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterConfig;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.index_api.IndexAPIEventWriterConfig;
import jakarta.annotation.PostConstruct;

public class ElasticsearchConfig extends AbstractDescribableImpl<ElasticsearchConfig> {

    private static final boolean DEFAULT_SAVE_ANNOTATIONS = true;
    private boolean saveAnnotations = DEFAULT_SAVE_ANNOTATIONS;

    private static final boolean DEFAULT_WRITE_ANNOTATIONS_TO_LOG_FILE = true;
    private boolean writeAnnotationsToLogFile = DEFAULT_WRITE_ANNOTATIONS_TO_LOG_FILE;

    private RunIdProvider runIdProvider = new DefaultRunIdProvider("");

    private EventWriterConfig eventWriterConfig;

    private static final int DEFAULT_SPLIT_MESSAGES_LONGER_THAN = 2000;
    private int splitMessagesLongerThan = DEFAULT_SPLIT_MESSAGES_LONGER_THAN;

    @DataBoundConstructor
    public ElasticsearchConfig() {
    }

    public RunIdProvider getRunIdProvider() {
        return runIdProvider;
    }

    @DataBoundSetter
    public void setRunIdProvider(RunIdProvider runIdProvider) {
        this.runIdProvider = runIdProvider;
    }

    public EventWriterConfig getEventWriterConfig() {
        return eventWriterConfig;
    }

    @DataBoundSetter
    public void setEventWriterConfig(EventWriterConfig config) {
        this.eventWriterConfig = config;
    }

    public int getSplitMessagesLongerThan() {
        return splitMessagesLongerThan;
    }

    @DataBoundSetter
    public void setSplitMessagesLongerThan(int chars) {
        this.splitMessagesLongerThan = chars;
    }

    public boolean isSaveAnnotations() {
        return saveAnnotations;
    }

    @DataBoundSetter
    public void setSaveAnnotations(boolean saveAnnotations) {
        this.saveAnnotations = saveAnnotations;
    }

    public boolean isWriteAnnotationsToLogFile() {
        return writeAnnotationsToLogFile;
    }

    @DataBoundSetter
    public void setWriteAnnotationsToLogFile(boolean writeAnnotationsToLogFile) {
        this.writeAnnotationsToLogFile = writeAnnotationsToLogFile;
    }

    @PostConstruct
    protected void init() {
        if (runIdProvider == null)
            throw new RuntimeException("runIdProvider must be set");
        if (eventWriterConfig == null)
            throw new RuntimeException("eventWriterConfig must be set");
    }

    @Extension
    @Symbol("elasticsearch")
    public static class DescriptorImpl extends Descriptor<ElasticsearchConfig> {

        public boolean defaultSaveAnnotations() {
            return DEFAULT_SAVE_ANNOTATIONS;
        }

        public boolean defaultWriteAnnotationsToLogFile() {
            return DEFAULT_WRITE_ANNOTATIONS_TO_LOG_FILE;
        }

        public int defaultSplitMessagesLongerThan() {
            return DEFAULT_SPLIT_MESSAGES_LONGER_THAN;
        }

        public EventWriterConfig defaultEventWriterConfig() {
            try {
                return new IndexAPIEventWriterConfig(null, null, null, null, null, null);
            } catch (URISyntaxException e) {
                return null;
            }
        }
    }
}
