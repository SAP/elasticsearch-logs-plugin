package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.Serializable;
import java.util.Map;

import javax.annotation.CheckForNull;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

/**
 * Information about a FlowNode to be sent with message events.
 *
 */
public class NodeInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    protected final String nodeId;
    protected final String stepName;
    protected final String stageName;
    protected final String stageId;
    protected final String parallelBranchName;
    protected final String parallelBranchId;
    protected final String agentName;
    protected final String displayName;

    public NodeInfo(FlowNode node) {

        FlowNode stage = getStage(node);
        FlowNode parallelBranch = getParallelBranch(node);
        String stageName = null;
        String stageId = null;
        String parallelBranchName = null;
        String parallelBranchId = null;

        if (stage != null) {
            stageId = stage.getId();
            LabelAction labelAction = stage.getAction(LabelAction.class);
            if (labelAction != null) {
                stageName = labelAction.getDisplayName();
            }
        }

        if (parallelBranch != null) {
            parallelBranchId = parallelBranch.getId();
            ThreadNameAction labelAction = parallelBranch.getAction(ThreadNameAction.class);
            if (labelAction != null) {
                parallelBranchName = labelAction.getThreadName();
            }
        }

        this.stepName = getStepName(node);
        this.agentName = getAgentName(node);
        this.nodeId = node.getId();
        this.stageName = stageName;
        this.stageId = stageId;
        this.parallelBranchName = parallelBranchName;
        this.parallelBranchId = parallelBranchId;
        this.displayName = node.getDisplayName();

    }

    /**
     * Appends this nodes info to the given map.
     *
     * @param data
     *            The map to receive the node info
     */
    public void appendNodeInfo(Map<String, Object> data) {
        data.put("flowNodeId", nodeId);
        if (stepName != null) {
            data.put("step", stepName);
        }
        if (stageName != null) {
            data.put("stageName", stageName);
        }
        if (stageId != null) {
            data.put("stageId", stageId);
        }
        if (parallelBranchName != null) {
            data.put("parallelBranchName", parallelBranchName);
        }
        if (parallelBranchId != null) {
            data.put("parallelBranchId", parallelBranchId);
        }
        if (agentName != null) {
            data.put("agent", agentName);
        }
        if (displayName != null) {
            data.put("displayName", displayName);
        }
    }

    /**
     * Returns the FlowNode of a stage step that encloses the given FlowNode or the current FlowNode if it is a stage.
     *
     * @param node
     *            The FlowNode to check.
     * @return The BlockStartNode representing the start of the stage step or null if not inside a
     *         stage.
     */
    private @CheckForNull FlowNode getStage(FlowNode node) {
        if (isStageNode(node)) {
            return node;
        }

        for (BlockStartNode bsn : node.iterateEnclosingBlocks()) {
            if (isStageNode(bsn)) {
                return bsn;
            }
        }
        return null;
    }

    private boolean isStageNode(FlowNode node) {
        if (node instanceof StepNode) {
            StepDescriptor descriptor = ((StepNode) node).getDescriptor();
            if (descriptor instanceof StageStep.DescriptorImpl) {
                LabelAction labelAction = node.getAction(LabelAction.class);
                if (labelAction != null) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns the FlowNode of a parallel branch step that encloses the given FlowNode.
     *
     * @param node
     *            The FlowNode to check.
     * @return The BlockStartNode representing the start of the parallel branch step or null if not
     *         inside a parallel branch.
     */
    private @CheckForNull FlowNode getParallelBranch(FlowNode node) {

        if (isParallelBranchNode(node)) {
            return node;
        }
        for (BlockStartNode bsn : node.iterateEnclosingBlocks()) {
            if (isParallelBranchNode(node)) {
                return bsn;
            }
        }

        return null;
    }

    private boolean isParallelBranchNode(FlowNode node) {
        if (node instanceof StepNode) {
            StepDescriptor descriptor = ((StepNode) node).getDescriptor();
            if (descriptor instanceof ParallelStep.DescriptorImpl) {
                ThreadNameAction threadNameAction = node.getAction(ThreadNameAction.class);
                if (threadNameAction != null) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns the name of the agent this FlowNode is running on.
     *
     * @param node
     *            The FlowNode to check
     * @return The name of the agent or null if not running on an agent.
     */
    private String getAgentName(FlowNode node) {
        for (BlockStartNode bsn : node.iterateEnclosingBlocks()) {
            if (bsn instanceof StepNode) {
                StepDescriptor descriptor = ((StepNode) bsn).getDescriptor();
                if (descriptor instanceof ExecutorStep.DescriptorImpl) {
                    WorkspaceAction workspaceAction = bsn.getAction(WorkspaceAction.class);
                    if (workspaceAction != null) {
                        return workspaceAction.getNode();
                    }
                }
            }
        }

        return null;
    }

    private String getStepName(FlowNode node) {
        String stepName = null;
        if (node instanceof StepNode) {
            StepDescriptor descriptor = ((StepNode) node).getDescriptor();
            if (descriptor != null) {
                stepName = descriptor.getFunctionName();
            }
        }
        return stepName;
    }

    @Override
    public String toString() {
        return String.format("Node: %s, Step: %s, Stage: %s (%s), Agent: %s", nodeId, stepName, stageName, stageId, agentName);
    }

}
