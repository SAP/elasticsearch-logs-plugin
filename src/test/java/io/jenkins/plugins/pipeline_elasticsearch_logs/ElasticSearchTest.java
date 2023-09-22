package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

import hudson.model.FreeStyleProject;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.DefaultRunIdProvider;

public class ElasticSearchTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    List<Map<String, Object>> elasticSearchLoggedLines = new ArrayList<>();

    @Before
    public void setup() throws Exception {
        ElasticsearchConfig config = new ElasticsearchConfig();
        config.setEventWriterConfig(
            new TestWriteAccess(eventData -> elasticSearchLoggedLines.add(eventData))
        );
        config.setRunIdProvider(new DefaultRunIdProvider("testInstance"));
        ElasticsearchGlobalConfig.get().setElasticsearch(config);
    }

    @After
    public void teardown() throws Exception {
        elasticSearchLoggedLines.clear();
    }

    @Test
    public void testSimpleJobOutput() throws Exception {
        FreeStyleProject project = jenkinsRule.createProject(FreeStyleProject.class);
        project.getBuildersList().add(new SleepBuilder(2));
        jenkinsRule.buildAndAssertSuccess(project);

        assertLogLine(0, "buildStart");
        assertLogLine(1, "buildMessage", "Legacy code started this job.  No cause information is available");
        assertLogLine(4, "buildMessage", "Sleeping 2ms");
        assertLogLine(5, "buildMessage", "Finished: SUCCESS");
        assertLogLine(6, "buildEnd");
        assertEquals(7, elasticSearchLoggedLines.size());
    }

    private void assertLogLine(int lineIndex, String eventType, String message) {
        assertLogLine(lineIndex, "test0", "1", eventType, message);
    }

    @Test
    public void testSimplePipelineOutput() throws Exception {
        WorkflowJob project = jenkinsRule.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition("node { echo message: 'hello' }", true));
        jenkinsRule.buildAndAssertSuccess(project);

        assertEquals(21, elasticSearchLoggedLines.size());

        assertLogLine(0, "buildStart");
        assertLogLine(1, "buildMessage", "Started");
        assertLogLine(2, "buildMessage", "[Pipeline] Start of Pipeline");
        assertLogLine(3, "flowGraph::flowStart");
        assertLogLine(4, "buildMessage", "[Pipeline] node");
        assertLogLine(5, "flowGraph::nodeStart");
        // skip 6
        assertLogLine(7, "buildMessage", "[Pipeline] {");
        assertLogLine(8, "flowGraph::nodeStart");
        assertLogLine(9, "buildMessage", "[Pipeline] echo");
        assertLogLine(10, "flowGraph::atomNodeStart");
        assertLogLine(11, "nodeMessage", "hello");
        assertLogLine(12, "buildMessage", "[Pipeline] }");
        assertLogLine(13, "flowGraph::atomNodeEnd");
        assertLogLine(14, "buildMessage", "[Pipeline] // node");
        assertLogLine(15, "flowGraph::nodeEnd");
        assertLogLine(16, "buildMessage", "[Pipeline] End of Pipeline");
        assertLogLine(17, "flowGraph::nodeEnd");
        assertLogLine(18, "flowGraph::flowEnd");
        assertLogLine(19, "buildMessage", "Finished: SUCCESS");
        assertLogLine(20, "buildEnd");
    }

    private void assertLogLine(int lineIndex, String eventType) {
        assertLogLine(lineIndex, eventType, null);
    }

    private void assertLogLine(int lineIndex, String project, String buildId, String eventType) {
        assertLogLine(lineIndex, project, buildId, eventType, null);
    }

    private void assertLogLine(int lineIndex, String project, String buildId, String eventType, String message) {
        Map<String, Object> line = elasticSearchLoggedLines.get(lineIndex);
        assertTrue(line.containsKey("timestampMillis"));
        assertTrue(line.containsKey("timestamp"));
        if (project != null && buildId != null) {
            Map<String, Object> runId = (Map<String, Object>) line.get("runId");
            assertEquals(project, runId.get("project"));
            assertEquals(buildId, runId.get("build"));
            assertEquals("testInstance", runId.get("instance"));
        }
        assertEquals(eventType, line.get("eventType"));
        if (message != null) assertEquals(message, line.get("message"));
    }

}
