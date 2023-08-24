package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchConfiguration.CONNECTION_TIMEOUT_DEFAULT;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.AssertionUtils.assertMatchEntries;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.AssertionUtils.assertMatchLines;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.LogUtils.removeAnnotations;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getExpectedTestJsonLog;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getExpectedTestLog;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getTestPipeline;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import hudson.model.Build;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.DefaultRunIdProvider;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.direct_es.ElasticSearchWriteAccessDirect;
import net.sf.json.JSONArray;

/**
 * The tests in this class are executed in a running Jenkins instance with different configurations
 * of the Elasticsearch plugin and with different Pipeline definitions.
 *
 * The expected entries sent to Elasticsearch are compared with the actual entries sent.
 * For this not an actual Elasticsearch instance is used but the {@link ElasticSearchWriteAccessMock} is used
 * which overrrides the {@link ElasticSearchWriteAccessDirect#push(String)} method which normally sends the data to
 * Elasticsearch.
 */
public class IntegrationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logs = new LoggerRule().record(Logger.getLogger(""),
            Level.WARNING);
    
    @Before
    public void before() {
    }

    @Test
    public void testFreestyleWithoutElasticsearchPlugin() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.scheduleBuild(new Cause.UserIdCause());
        Build<?, ?> build;
        while ((build = project.getLastBuild()) == null || build.getResult() == null) {
            Thread.sleep(100);
        }
        Assert.assertEquals(Result.SUCCESS, build.getResult());

        while (!build.getLogText().isComplete()) {
            Thread.sleep(10);
        }

        String expectedLog = getExpectedTestLog();
        String log = removeAnnotations(build.getLogText());
        assertMatchLines(expectedLog, log);
    }

    @Test
    public void testPipelineWithoutElasticsearchPlugin() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(getTestPipeline(), true));
        project.scheduleBuild(new Cause.UserIdCause());
        WorkflowRun build;
        while ((build = project.getLastBuild()) == null || build.getResult() == null) {
            Thread.sleep(100);
        }
        Assert.assertEquals(Result.SUCCESS, project.getLastBuild().getResult());

        while (!build.getLogText().isComplete()) {
            Thread.sleep(10);
        }

        String expectedLog = getExpectedTestLog();
        String log = removeAnnotations(build.getLogText());
        assertMatchLines(expectedLog, log);
    }

    @Test
    public void testPipelineWithElasticsearchPlugin() throws Exception {
        ElasticSearchWriteAccessMock mockWriter = new ElasticSearchWriteAccessMock(false);
        configureElasticsearchPlugin(true, false, mockWriter);

        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(getTestPipeline(), true));
        WorkflowRun build;
        project.scheduleBuild(new Cause.UserIdCause());
        while ((build = project.getLastBuild()) == null || build.getResult() == null) {
            Thread.sleep(100);
        }

        Assert.assertEquals(Result.SUCCESS, project.getLastBuild().getResult());

        JSONArray expectedLog = getExpectedTestJsonLog();
        assertMatchEntries(expectedLog, mockWriter.getEntries());
    }

    @Test
    public void testPipelineWithElasticsearchPluginReadLogsFromFile() throws Exception {
        ElasticSearchWriteAccessMock mockWriter = new ElasticSearchWriteAccessMock(false);
        configureElasticsearchPlugin(true, false, mockWriter);

        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(getTestPipeline(), true));
        WorkflowRun build;
        project.scheduleBuild(new Cause.UserIdCause());
        while ((build = project.getLastBuild()) == null || build.getResult() == null) {
            Thread.sleep(100);
        }

        Assert.assertEquals(Result.SUCCESS, project.getLastBuild().getResult());
        File logFile = new File(build.getRootDir(), "log");
        Assert.assertThat(logFile.exists(), equalTo(true));

        JSONArray expectedJsonLog = getExpectedTestJsonLog();
        assertMatchEntries(expectedJsonLog, mockWriter.getEntries());
        
        String expectedLog = getExpectedTestLog();
        String log = removeAnnotations(build.getLogText());
        assertMatchLines(expectedLog, log);
    }

    @Test
    public void testPipelinePushLogsWithConnectionIssues() throws Exception {
        // SETUP
        ElasticSearchWriteAccess elasticSearchAccess = new ElasticSearchWriteAccessDirect(new URI("http://wrongurl.does.not.exist"), null, null, CONNECTION_TIMEOUT_DEFAULT, null);
        configureElasticsearchPlugin(true, false, elasticSearchAccess);

        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(getTestPipeline(), true));
        WorkflowRun build;
        logs.capture(9999);
        
        // EXERCISE
        project.scheduleBuild(new Cause.UserIdCause());
        while ((build = project.getLastBuild()) == null || build.getResult() == null) {
            Thread.sleep(100);
        }

        // VERIFY
        Assert.assertEquals(Result.SUCCESS, project.getLastBuild().getResult());

        List<LogRecord> records = new ArrayList<>(logs.getRecords());
        Collections.reverse(records);
        Iterator<LogRecord> logEntries = records.iterator();

        //Log entry with full details
        String expectedPrefix = "Could not push log to Elasticsearch - ErrorID: '";
        LogRecord logEntryWithFullInfos = findNext(expectedPrefix, logEntries);
        Assert.assertTrue("Log does not contain: " + expectedPrefix, logEntryWithFullInfos != null);
        String message = logEntryWithFullInfos.getMessage();
        Assert.assertNotNull("The full log entry does not contain the cause (has to)", logEntryWithFullInfos.getThrown());

        String errorId = message.substring(expectedPrefix.length(), message.length()-1).trim();
        
        //Stripped log entry without sensitive information
        String expectedMessage = "Could not push log to Elasticsearch - Search Jenkins log for ErrorID '" + errorId + "'";
        LogRecord logEntryStripped = findNext(expectedMessage, logEntries);
        if(logEntryStripped == null) {
            File logFile = persistLog(logs);
            Assert.fail("Log does not contain: " + expectedMessage + " - see " + logFile.getAbsolutePath());
        }
        Throwable theException = findExceptionWithMessage(logEntryStripped, expectedMessage);
        Assert.assertNotNull("Could not find the stripped exception", theException);
        Assert.assertNull("The stripped exception contains a cause (must not): " + theException.getCause() , theException.getCause());
    }

    private File persistLog(LoggerRule logs2) throws IOException {
        File file = File.createTempFile(this.getClass().getSimpleName(), ".log");
        FileUtils.writeLines(file, logs.getMessages());
        return file;
    }

    private LogRecord findNext(String string, Iterator<LogRecord> logEntries) {
        while(logEntries.hasNext()) {
            LogRecord entry = logEntries.next();
            if(entry.getMessage().contains(string)) return entry;
            if(entry.getThrown() != null && entry.getThrown().getMessage().contains(string)) return entry;
        }
        return null;
    }

    private Throwable findExceptionWithMessage(LogRecord logEntry, String expectedMessage) {
        Throwable cause = logEntry.getThrown();
        if(cause.getMessage().contains(expectedMessage)) return cause;
        while((cause = cause.getCause()) != null) {
            if(cause.getMessage().contains(expectedMessage)) return cause;
        }
        return null;
    }

    @Test
    public void testPipelineWithSkippedStages() throws Exception {
        ElasticSearchWriteAccessMock mockWriter = new ElasticSearchWriteAccessMock(false);
        configureElasticsearchPlugin(true, false, mockWriter);

        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(getTestPipeline(), true));
        WorkflowRun build;
        project.scheduleBuild(new Cause.UserIdCause());
        while ((build = project.getLastBuild()) == null || build.getResult() == null) {
            Thread.sleep(100);
        }

        Assert.assertEquals(Result.FAILURE, project.getLastBuild().getResult());

        JSONArray expectedLog = getExpectedTestJsonLog();
        assertMatchEntries(expectedLog, mockWriter.getEntries());
    }

    private void configureElasticsearchPlugin(boolean activate, boolean readLogsFromEs, ElasticSearchWriteAccess mockWriter) throws URISyntaxException {
        ElasticSearchGlobalConfiguration globalConfig = ElasticSearchGlobalConfiguration.get();

        ElasticSearchConfiguration config = null;
        if (activate) {
            config = new TestConfig("http://localhost:9200/jenkins_logs/_doc", mockWriter);
            config.setRunIdProvider(new DefaultRunIdProvider("test_instance"));
        }

        globalConfig.setElasticSearch(config);
        globalConfig.save();
    }

    private static class TestConfig extends ElasticSearchConfiguration {

        private ElasticSearchWriteAccess mockWriter;

        public TestConfig(String url, ElasticSearchWriteAccess mockWriter) throws URISyntaxException {
            super(url);
            this.mockWriter = mockWriter;
        }

        @Override
        protected SerializableSupplier<ElasticSearchWriteAccess> getWriteAccessFactory() {
            return () -> mockWriter;
        }
    }

}
