package io.jenkins.plugins.pipeline_elasticsearch_logs.read;

import java.util.function.Supplier;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.Executable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.ConsoleAnnotators;

import hudson.ExtensionPoint;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public abstract class ElasticSearchReadAccess extends AbstractDescribableImpl<ElasticSearchReadAccess> implements ExtensionPoint {

    public static abstract class ElasticSearchReadAccessDescriptor extends Descriptor<ElasticSearchReadAccess> {
        protected ElasticSearchReadAccessDescriptor() {
        }
    }

    /**
     * For serialization return a supplier providing a deserialized instance
     * of this object at a later point in time.
     * @return
     */
    public abstract Supplier<ElasticSearchReadAccess> getSupplier();

    /**
     * Provides an alternate way of retrieving output from a build.
     * <p>In an {@link AnnotatedLargeText#writeHtmlTo} override, {@link ConsoleAnnotationOutputStream#eol}
     * should apply {@link #startStep} and {@link #endStep} to delineate blocks contributed by steps.
     * (Also see {@link ConsoleAnnotators}.)
     * @param complete if true, we claim to be serving the complete log for a build,
     *                  so implementations should be sure to retrieve final log lines
     * @return a log
     */
    @CheckForNull public abstract AnnotatedLargeText<Executable> overallLog(Executable build, boolean complete);
    
    /**
     * Provides an alternate way of retrieving output from a build.
     * @param node a running node
     * @param complete if true, we claim to be serving the complete log for a node,
     *                  so implementations should be sure to retrieve final log lines
     * @return a log for this just this node
     * @see LogAction
     */
     @CheckForNull public abstract AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete);

}
