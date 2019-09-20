package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.AssertionUtils.assertMatchEntries;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.AssertionUtils.assertMatchLines;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.LogUtils.removeAnnotations;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getExpectedTestJsonLog;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getExpectedTestLog;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getTestPipeline;

import java.net.URISyntaxException;
import java.util.function.Supplier;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.Build;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.DefaultRunIdProvider;
import net.sf.json.JSONArray;


/**
 * The tests in this class are executed in a running Jenkins instance with different configurations
 * of the Elasticsearch plugin and with different Pipeline definitions.
 * 
 * The expected entries sent to Elasticsearch are compared with the actual entries sent.
 * For this not an actual Elasticsearch instance is used but the {@link ElasticSearchAccessMock} is used
 * which overrrides the {@link ElasticSearchAccess#push(String)} method which normally sends the data to
 * Elasticsearch.
 */
public class IntegrationTest
{
  
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Before
  public void before() {
  }
  
  
  @Test
  public void testFreestyleWithoutElasticsearchPlugin() throws Exception {
    FreeStyleProject project = j.createFreeStyleProject();
    project.scheduleBuild(new Cause.UserIdCause());
    Build<?,?> build;
    while((build = project.getLastBuild()) == null || build.getResult() == null) {
      Thread.sleep(100);
    }
    Assert.assertEquals(Result.SUCCESS, build.getResult());
    
    while(!build.getLogText().isComplete()) {
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
    while((build = project.getLastBuild()) == null || build.getResult() == null) {
      Thread.sleep(100);
    }
    Assert.assertEquals(Result.SUCCESS, project.getLastBuild().getResult());

    while(!build.getLogText().isComplete()) {
      Thread.sleep(10);
    }

    String expectedLog = getExpectedTestLog();
    String log = removeAnnotations(build.getLogText());
    assertMatchLines(expectedLog, log);
  }


  @Test
  public void testPipelineWithElasticsearchPlugin() throws Exception {
    ElasticSearchAccessMock mockWriter = new ElasticSearchAccessMock(false);
    configureElasticsearchPlugin(true, mockWriter);

    WorkflowJob project = j.createProject(WorkflowJob.class);
    project.setDefinition(new CpsFlowDefinition(getTestPipeline(), true));
    WorkflowRun build;
    project.scheduleBuild(new Cause.UserIdCause());
    while((build = project.getLastBuild()) == null || build.getResult() == null) {
      Thread.sleep(100);
    }
    
    Assert.assertEquals(Result.SUCCESS, project.getLastBuild().getResult());
    
    //Jenkins build log is empty if ES plugin is active, and read from ES in not enabled.
    Assert.assertEquals("", build.getLog());
    
    JSONArray expectedLog = getExpectedTestJsonLog();
    assertMatchEntries(expectedLog, mockWriter.getEntries());
  }

  @Test
  public void testPipelineWithSkippedStages() throws Exception {
    ElasticSearchAccessMock mockWriter = new ElasticSearchAccessMock(false);
    configureElasticsearchPlugin(true, mockWriter);

    WorkflowJob project = j.createProject(WorkflowJob.class);
    project.setDefinition(new CpsFlowDefinition(getTestPipeline(), true));
    WorkflowRun build;
    project.scheduleBuild(new Cause.UserIdCause());
    while((build = project.getLastBuild()) == null || build.getResult() == null) {
      Thread.sleep(100);
    }
    
    Assert.assertEquals(Result.FAILURE, project.getLastBuild().getResult());
    
    //Jenkins build log is empty if ES plugin is active, and read from ES in not enabled.
    Assert.assertEquals("", build.getLog());
    
    JSONArray expectedLog = getExpectedTestJsonLog();
    assertMatchEntries(expectedLog, mockWriter.getEntries());
  }


  private void configureElasticsearchPlugin(boolean activate, ElasticSearchAccessMock mockWriter) throws URISyntaxException {
    ElasticSearchGlobalConfiguration globalConfig = ElasticSearchGlobalConfiguration.get();

    ElasticSearchConfiguration config = null;
    if(activate) {
      config = new TestConfig("http://localhost:9200/jenkins_logs/_doc", mockWriter);
      config.setRunIdProvider(new DefaultRunIdProvider("test_instance"));
    }

    globalConfig.setElasticSearch(config);
    globalConfig.save();
  }


  private static class TestConfig extends ElasticSearchConfiguration {

    private ElasticSearchAccessMock mockWriter;

    public TestConfig(String url, ElasticSearchAccessMock mockWriter) throws URISyntaxException {
        super(url);
        this.mockWriter = mockWriter;
    }

    @Override
    protected Supplier<ElasticSearchAccess> getAccessFactory() {
      return () -> mockWriter;
    }

  }
  
}