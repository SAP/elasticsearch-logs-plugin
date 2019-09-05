package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.AssertionUtils.assertMatchEntries;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.AssertionUtils.assertMatchLines;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.LogUtils.removeAnnotations;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getExpectedTestJsonLog;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getExpectedTestLog;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.testutils.ResourceUtils.getTestPipeline;

import java.io.IOException;
import java.net.URISyntaxException;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

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
 * For this not an actual Elasticsearch instance is used but the {@link ElasticSearchWriterMock} is used
 * which overrrides the {@link ElasticSearchWriter#push(String)} method which normally sends the data to
 * Elasticsearch.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ElasticSearchWriter.class})
@PowerMockIgnore({"javax.crypto.*"}) //this needs to be excluded to prevent errors
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
    ElasticSearchWriterMock mockWriter = mockWriter();
    configureElasticsearchPlugin(true);

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
    ElasticSearchWriterMock mockWriter = mockWriter();
    configureElasticsearchPlugin(true);

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
  
  
  /**
   * We need to make {@link ElasticSearchWriter#createElasticSearchWriter(ElasticSearchRunConfiguration)}
   * return our mocked {@link ElasticSearchWriterMock}.
   * @return
   * @throws IOException
   * @throws URISyntaxException
   */
  private ElasticSearchWriterMock mockWriter() throws IOException, URISyntaxException {
    ElasticSearchWriterMock elasticSearchWriterMock = new ElasticSearchWriterMock(false);
    PowerMockito.mockStatic(ElasticSearchWriter.class);
    Mockito.when(ElasticSearchWriter.createElasticSearchWriter(Mockito.any())).thenReturn(elasticSearchWriterMock);
    return elasticSearchWriterMock;
  }
  
  private void configureElasticsearchPlugin(boolean activate) throws URISyntaxException {
    ElasticSearchGlobalConfiguration globalConfig = ElasticSearchGlobalConfiguration.get();

    ElasticSearchConfiguration config = null;
    if(activate) {
      config = new ElasticSearchConfiguration("http://localhost:9200/jenkins_logs/_doc");
      config.setRunIdProvider(new DefaultRunIdProvider("test_instance"));
    }

    globalConfig.setElasticSearch(config);
    globalConfig.save();
  }
  
}