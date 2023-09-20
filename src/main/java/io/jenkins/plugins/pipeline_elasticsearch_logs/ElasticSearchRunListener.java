package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.pipeline_elasticsearch_logs.utils.RunUtils;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;

@Extension
public class ElasticSearchRunListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(ElasticSearchRunListener.class.getName());

    private final Map<String, EventWriter> eventWritersByRunId = new HashMap<>();

    @Override
    public void onInitialize(Run<?, ?> run) {

        try {
            ElasticSearchRunConfiguration config = ElasticSearchRunConfiguration.get(run);
            if (config == null) {
                return;
            }

            EventWriter eventWriter = config.createEventWriter();
            this.eventWritersByRunId.put(RunUtils.getUniqueRunId(run), eventWriter);

            Map<String, Object> data = config.createData();
            data.put("eventType", "buildStart");
            eventWriter.push(data);
        }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get Executable of FlowExecution.", e);
        }
    }

    @Override
    public void onFinalized(Run<?, ?> run) {
        EventWriter writer = null;
        try {
            ElasticSearchRunConfiguration config = ElasticSearchRunConfiguration.get(run);
            if (config == null) {
                return;
            }
            writer = this.eventWritersByRunId.remove(RunUtils.getUniqueRunId(run));
            if (writer == null) {
                LOGGER.log(Level.SEVERE, "internal inconsistency: Elasticsearch event writer not found although it should have been created at the start of the run");
                return;
            }

            Map<String, Object> data = config.createData();
            data.put("eventType", "buildEnd");
            Result result = run.getResult();
            if (result != null) {
                data.put("result", result.toString());
            }
            long duration = run.getDuration();
            if (duration > 0) {
                data.put("duration", run.getDuration());
            }
            writer.push(data);
        }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get Executable of FlowExecution.");
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "failed to close event writer", ex);
                }
            }
            ElasticSearchRunConfiguration.release(run);
        }
    }
}
