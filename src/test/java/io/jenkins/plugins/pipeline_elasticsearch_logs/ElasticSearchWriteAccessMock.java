package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchConfiguration.CONNECTION_TIMEOUT_DEFAULT;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.JSONUtils.prettyPrint;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.http.conn.ConnectTimeoutException;

import io.jenkins.plugins.pipeline_elasticsearch_logs.write.direct_es.ElasticSearchWriteAccessDirect;

/**
 * This class mocks an ElasticSearchAccess and overrides the {@link ElasticSearchWriteAccessDirect#push(String)} method.
 * The data the plugin tries to send to Elasticsearch are collected and can be retrieved via {@link #getEntries()}.
 */
public class ElasticSearchWriteAccessMock extends ElasticSearchWriteAccessDirect {

    private ArrayList<String> entries = new ArrayList<>();
    private boolean printToLog = false;
    private boolean failConnection = false;

    public ElasticSearchWriteAccessMock(boolean printToLog) throws URISyntaxException {
        super(new URI("http://localhost:9200/jenkins/_doc"), "test", "test", CONNECTION_TIMEOUT_DEFAULT, null);
        this.printToLog = printToLog;
    }

    @Override
    public String testConnection() throws URISyntaxException, IOException {
        return "";
    }

    @Override
    public void push(Map<String, Object> data) throws IOException {
        if(failConnection) {
            throw new ConnectTimeoutException("Connect to Elasticsearch failed: connect timed out");
        }
        String dataString = prettyPrint(data);
        if (printToLog) {
            System.out.println(dataString);
        }
        entries.add(dataString);
    }

    /**
     * @return all entries the plugin tried to sent to Elasticsearch
     */
    public ArrayList<String> getEntries() {
        return entries;
    }

    public void failConnection(boolean failConnection) {
        this.failConnection = failConnection;
    }

}
