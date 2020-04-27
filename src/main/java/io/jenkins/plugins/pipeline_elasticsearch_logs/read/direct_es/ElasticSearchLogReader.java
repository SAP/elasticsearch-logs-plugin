package io.jenkins.plugins.pipeline_elasticsearch_logs.read.direct_es;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.Utils.logExceptionAndReraiseWithTruncatedDetails;
import static java.lang.String.format;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.Executable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import hudson.console.AnnotatedLargeText;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ConsoleNotes;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchRunConfiguration;
import net.sf.json.JSONArray;

public class ElasticSearchLogReader {

    private final static int QUERY_SIZE = 9999;
    private static final TimeValue SCROLL_TIME_VALUE = TimeValue.timeValueSeconds(60);

    private static final Logger LOGGER = Logger.getLogger(ElasticSearchLogReader.class.getName());

    private final String uid;
    private final ElasticSearchRunConfiguration config;
    private final RestHighLevelClient client;

    public ElasticSearchLogReader(ElasticSearchRunConfiguration config, RestHighLevelClient client) throws IOException {
        this.client = client;
        this.config = config;
        this.uid = config.getUid();
    }

    AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean completed) throws IOException {
        ByteBuffer buf = new ByteBuffer();
        readFromElasticsearch(buf, node.getId());
        return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, completed, node);
    }

    AnnotatedLargeText<Executable> overallLog(Executable build, boolean complete) throws IOException {
        ByteBuffer buf = new ByteBuffer();
        readFromElasticsearch(buf, null);
        return new AnnotatedLargeText<FlowExecutionOwner.Executable>(buf, StandardCharsets.UTF_8, complete, build);
    }

    private void readFromElasticsearch(OutputStream os, @CheckForNull String nodeId) throws IOException {
        try (Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            queryElasticSearch(w, nodeId, null);
            w.flush();
        } catch (RuntimeException x) {
            logExceptionAndReraiseWithTruncatedDetails(LOGGER, Level.SEVERE, "Could not read from Elasticsearch", x);
        }
    }

    private void queryElasticSearch(Writer writer, @CheckForNull String nodeId, @CheckForNull JSONArray searchAfter) throws IOException {
        BoolQueryBuilder qb = QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("uid", uid))
                .must(QueryBuilders.matchQuery("eventType", "nodeMessage buildMessage"));
        if (nodeId != null) qb.must(QueryBuilders.matchQuery("flowNodeId", nodeId));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().size(QUERY_SIZE).sort("timestampMillis", SortOrder.ASC)
                .query(qb);
        SearchRequest searchRequest = new SearchRequest().indices(config.getIndices()).source(searchSourceBuilder)
                .scroll(SCROLL_TIME_VALUE);

        String reqId = UUID.randomUUID().toString();
        LOGGER.log(Level.FINE, format("SearchRequest[%s] - %s: %s", reqId, config.getUri(), searchRequest.toString()));
        SearchResponse scrollResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        LOGGER.log(Level.FINER, format("SearchResponse[%s] hits: %s", reqId, scrollResponse.getHits().getTotalHits()));
        try (ScrollingEntryIterable entryIterable = new ScrollingEntryIterable(client, scrollResponse, reqId)) {
            for (SearchHit hit : entryIterable) {
                ConsoleNotes.write(writer, hit.getSourceAsMap());
            }
        }
    }

    private static class ScrollingEntryIterable implements Iterable<SearchHit>, AutoCloseable {

        private SearchResponse scrollResponse;
        private final RestHighLevelClient client;
        private final String reqId;

        public ScrollingEntryIterable(RestHighLevelClient client, SearchResponse scrollResponse, String reqId) {
            this.client = client;
            this.scrollResponse = scrollResponse;
            this.reqId = reqId;
        }

        @Override
        public Iterator<SearchHit> iterator() {
            return new Iterator<SearchHit>() {
                private int currentIndex = 0;

                @Override
                public boolean hasNext() {
                    // An empty SearchHits array means that there are no more elements.
                    // This condition occurs after the next() methods returned the last entry.
                    return scrollResponse.getHits().getHits().length != 0;
                }

                @Override
                // This method reloads new entries after returning the last one from the current
                // batch.
                public SearchHit next() {
                    SearchHit[] hits = scrollResponse.getHits().getHits();
                    if (currentIndex < hits.length) {
                        SearchHit searchHit = hits[currentIndex++];
                        if (currentIndex == hits.length) {
                            try {
                                // Preemptively request next SearchScroll batch.
                                requestNextSearchScrollBatch();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return searchHit;
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                private void requestNextSearchScrollBatch() throws IOException {
                    LOGGER.log(Level.FINE,
                            format("SearchRequest[%s]: Preemptive scroll search with id '%s'", reqId, scrollResponse.getScrollId()));
                    SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollResponse.getScrollId()).scroll(SCROLL_TIME_VALUE);
                    scrollResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
                    LOGGER.log(Level.FINER, format("SearchResponse[%s] scroll hits: %s", reqId, scrollResponse.getHits().getTotalHits()));
                    currentIndex = 0;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public void close() {
            LOGGER.finer(String.format("Request to clear scroll search with id '%s'", scrollResponse.getScrollId()));

            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollResponse.getScrollId());

            client.clearScrollAsync(clearScrollRequest, RequestOptions.DEFAULT, new ActionListener<ClearScrollResponse>() {
                @Override
                public void onResponse(ClearScrollResponse response) {
                    // noop
                }

                @Override
                public void onFailure(Exception e) {
                    // noop
                }
            });
        }
    }

}
