package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.JSONUtils.prettyPrint;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.http.conn.ConnectTimeoutException;

import hudson.model.Run;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterConfig;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterRunConfig;

/**
 * An {@link EventWriter} for test purposed that records all events
 * that have been pushed to it.
 */
public class EventWriterMock extends EventWriterConfig
    implements EventWriterRunConfig, EventWriter {

    private ArrayList<String> events = new ArrayList<>();
    private boolean printToLog = false;
    private boolean failConnection = false;

    public EventWriterMock(boolean printToLog) throws URISyntaxException {
        super();
        this.printToLog = printToLog;
    }

    public void failConnection(boolean failConnection) {
        this.failConnection = failConnection;
    }

    @Override
    public EventWriterRunConfig createRunConfig(Run<?, ?> run) {
        return this;
    }

    @Override
    public EventWriter createEventWriter() {
        return this;
    }

    @Override
    public void push(Map<String, Object> data) throws IOException {
        if (failConnection) {
            throw new ConnectTimeoutException("Connect to Elasticsearch failed: connect timed out");
        }
        String dataString = prettyPrint(data);
        if (printToLog) {
            System.out.println(dataString);
        }
        events.add(dataString);
    }

    /**
     * @return all entries the plugin tried to sent to Elasticsearch
     */
    public ArrayList<String> getEvents() {
        return events;
    }

    @Override
    public void close() throws IOException {
    }
}
