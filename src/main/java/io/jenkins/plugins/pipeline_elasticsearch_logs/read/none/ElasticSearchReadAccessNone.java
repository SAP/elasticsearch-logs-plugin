package io.jenkins.plugins.pipeline_elasticsearch_logs.read.none;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.Executable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import hudson.Extension;
import hudson.console.AnnotatedLargeText;
import io.jenkins.plugins.pipeline_elasticsearch_logs.read.ElasticSearchReadAccess;

public class ElasticSearchReadAccessNone extends ElasticSearchReadAccess {

    private static final Logger LOGGER = Logger.getLogger(ElasticSearchReadAccessNone.class.getName());

    @DataBoundConstructor
    public ElasticSearchReadAccessNone() {
    }
    
    @Extension
    @Symbol("noRead")
    public static class DescriptorImpl extends ElasticSearchReadAccessDescriptor {
        @Override
        public String getDisplayName() {
            return "None Elasticsearch Reader";
        }
    }

    @Override
    public AnnotatedLargeText<Executable> overallLog(Executable build, boolean complete) {
        LOGGER.finest(this.getClass().getSimpleName()+".overallLog()");
        return new AnnotatedLargeText<>(new ByteBuffer(), StandardCharsets.UTF_8, true, build);
    }

    @Override
    public AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete) {
        LOGGER.finest(this.getClass().getSimpleName()+".stepLog()");
        return new AnnotatedLargeText<>(new ByteBuffer(), StandardCharsets.UTF_8, true, node);
    }

    private static class MeSupplier implements Supplier<ElasticSearchReadAccess>, Serializable {

        private static final long serialVersionUID = 1L;

        private MeSupplier(ElasticSearchReadAccessNone me) {
        }
        
        @Override
        public ElasticSearchReadAccess get() {
            try {
                return new ElasticSearchReadAccessNone();
            } catch (Exception e) {
                throw new RuntimeException("Could not create ElasticSearchReadAccessNone", e);
            }
        }        
    }

    @Override
    public Supplier<ElasticSearchReadAccess> getSupplier() {
        return new MeSupplier(this);
    }

}
