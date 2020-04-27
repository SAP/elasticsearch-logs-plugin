package io.jenkins.plugins.pipeline_elasticsearch_logs.read.direct_es;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.Executable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.console.AnnotatedLargeText;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchGlobalConfiguration;
import io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchRunConfiguration;
import io.jenkins.plugins.pipeline_elasticsearch_logs.read.ElasticSearchReadAccess;

public class ElasticSearchReadAccessDirect extends ElasticSearchReadAccess {

    private static final Logger LOGGER = Logger.getLogger(ElasticSearchReadAccessDirect.class.getName());

    @DataBoundConstructor
    public ElasticSearchReadAccessDirect() {
    }

    @Extension
    @Symbol("esDirectRead")
    public static class DescriptorImpl extends ElasticSearchReadAccessDescriptor {
        @Override
        public String getDisplayName() {
            return "Direct Elasticsearch Reader";
        }
    }

    @Override
    public AnnotatedLargeText<Executable> overallLog(Executable build, boolean complete) {
        LOGGER.info(this.getClass().getSimpleName()+".overallLog()");
        try {
            if (build instanceof WorkflowRun) {
                ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration.getRunConfiguration((WorkflowRun) build);
                if(config != null) {
                    return new ElasticSearchLogReader(config, createNewRestClient(config)).overallLog(build, complete);
                } else {
                    LOGGER.log(Level.SEVERE, "Could not get RunConfiguration. Plugin not activated?");
                    return null;
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not get overallLog", e);
            return null;
        }
    }

    @Override
    public AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete) {
        LOGGER.info(this.getClass().getSimpleName()+".stepLog()");
        try {
            hudson.model.Queue.Executable executable = node.getExecution().getOwner().getExecutable();
            if (executable instanceof WorkflowRun) {
                ElasticSearchRunConfiguration config = ElasticSearchGlobalConfiguration.getRunConfiguration((WorkflowRun) executable);
                if(config != null) {
                    return new ElasticSearchLogReader(config, createNewRestClient(config)).stepLog(node, complete);
                } else {
                    LOGGER.log(Level.SEVERE, "Could not get RunConfiguration. Plugin not activated?");
                    return null;
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not get stepLog", e);
            return null;
        }
    }

    private RestHighLevelClient createNewRestClient(ElasticSearchRunConfiguration config) {
        URI uri = config.getUri();
        String username = config.getUsername();
        String password = config.getPassword();
        RestClientBuilder builder = RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()));
        String auth = null;
        if (auth == null && StringUtils.isNotBlank(username)) {
            auth = Base64.encodeBase64String((username + ":" + StringUtils.defaultString(password)).getBytes(StandardCharsets.UTF_8));
        }
        if(auth != null) builder.setDefaultHeaders(new Header[] { new BasicHeader("Authorization", "Basic " + auth) });
        return new RestHighLevelClient(builder);
    }

    private static class MeSupplier implements Supplier<ElasticSearchReadAccess>, Serializable {

        private static final long serialVersionUID = 1L;

        private MeSupplier(ElasticSearchReadAccessDirect me) {
        }
        
        @Override
        public ElasticSearchReadAccess get() {
            try {
                return new ElasticSearchReadAccessDirect();
            } catch (Exception e) {
                throw new RuntimeException("Could not create ElasticSearchReadAccessDirect", e);
            }
        }        
    }

    @Override
    public Supplier<ElasticSearchReadAccess> getSupplier() {
        return new MeSupplier(this);
    }

}
