package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;

import hudson.model.Result;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class ElasticSearchGraphListener implements GraphListener.Synchronous {
    private static final String FLOW_GRAPH_ATOM_NODE_END = "flowGraph::atomNodeEnd";

    private static final String FLOW_GRAPH_NODE_END = "flowGraph::nodeEnd";

    private static final String FLOW_GRAPH_NODE_START = "flowGraph::nodeStart";

    private static final String FLOW_GRAPH_FLOW_END = "flowGraph::flowEnd";

    private static final String FLOW_GRAPH_FLOW_START = "flowGraph::flowStart";

    private static final String FLOW_GRAPH_ATOM_NODE_START = "flowGraph::atomNodeStart";

    private static final String PREDECESSORS = "predecessors";

    private static final String ERROR_MESSAGE = "errorMessage";

    private static final String START_ID = "startId";

    private static final String DURATION = "duration";

    private static final String RESULT = "result";

    static final String EVENT_TYPE = "eventType";

    private static final Logger LOGGER = Logger.getLogger(ElasticSearchGraphListener.class.getName());

    private final ElasticSearchAccess writer;
    private final ElasticSearchRunConfiguration config;

    public ElasticSearchGraphListener(ElasticSearchRunConfiguration config) throws IOException {
        writer = config.createAccess();
        this.config = config;
    }

    @Override
    public void onNewHead(FlowNode node) {
        try {
            // We cannot send StepEndNodes directly since information is missing, like an
            // ErrorAction (see example below).
            // There might be more cases which need to be considered like this. Almost all
            // parents change compared to their initial state when passed this method.
            // 1st time direct: StepEndNode[8 - 'Bind credentials to variables : End',
            // enclosing: 6, startNode: 7, error: null, actions: TimingAction]
            // 2nd time as parent of #9: StepEndNode[8 - 'Bind credentials to variables :
            // End', enclosing: 6, startNode: 7, error: CredentialNotFoundException,
            // actions: TimingAction,ErrorAction]
            for (FlowNode parent : node.getParents()) {
                if (parent instanceof AtomNode) {
                    sendAtomNodeEnd(parent, node);
                } else if (parent instanceof BlockEndNode) {
                    sendNodeEnd((BlockEndNode<?>) parent);
                }
            }

            if (node instanceof AtomNode || node instanceof BlockStartNode) {
                sendNodeStart(node);
            }
            if (node instanceof FlowEndNode) {
                sendNodeEnd((FlowEndNode) node);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to push data to Elastic Search", e);
        }
    }

    private String getEventType(FlowNode node) {
        if (node instanceof AtomNode) {
            return FLOW_GRAPH_ATOM_NODE_START;
        }
        if (node instanceof FlowStartNode) {
            return FLOW_GRAPH_FLOW_START;
        }
        if (node instanceof FlowEndNode) {
            return FLOW_GRAPH_FLOW_END;
        }
        if (node instanceof BlockStartNode) {
            return FLOW_GRAPH_NODE_START;
        }
        if (node instanceof BlockEndNode) {
            return FLOW_GRAPH_NODE_END;
        }

        return "unknown";
    }

    private void sendAtomNodeEnd(FlowNode node, FlowNode successor) throws IOException {
        Map<String, Object> data = createData(node);
        data.put(EVENT_TYPE, FLOW_GRAPH_ATOM_NODE_END);
        data.put(RESULT, getStatus(node));
        data.put(DURATION, getDuration(node, successor));
        String errorMessage = getErrorMessage(node);
        if (errorMessage != null) {
            data.put(ERROR_MESSAGE, errorMessage);
        }

        writer.push(JSONObject.fromObject(data).toString());
    }

    private void sendNodeEnd(BlockEndNode<?> node) throws IOException {
        Map<String, Object> data = createData(node);
        data.put(EVENT_TYPE, getEventType(node));
        FlowNode startNode = node.getStartNode();
        data.put(START_ID, startNode.getId());

        data.put(RESULT, getStatus(node));
        data.put(DURATION, getDuration(startNode, node));

        String errorMessage = getErrorMessage(node);
        if (errorMessage != null) {
            data.put(ERROR_MESSAGE, errorMessage);
        }

        writer.push(JSONObject.fromObject(data).toString());
    }

    private void sendNodeStart(FlowNode node) throws IOException {
        Map<String, Object> data = createData(node);

        data.put(EVENT_TYPE, getEventType(node));
        writer.push(JSONObject.fromObject(data).toString());
    }

    private long getDuration(FlowNode startNode, FlowNode endNode) {
        return TimingAction.getStartTime(endNode) - TimingAction.getStartTime(startNode);
    }

    private Map<String, Object> createData(FlowNode node) throws IOException {
        Map<String, Object> data = config.createData();
        List<FlowNode> predecessors = node.getParents();
        if (predecessors.size() > 0) {
            JSONArray p = new JSONArray();
            for (FlowNode parent : predecessors) {
                p.add(parent.getId());
            }
            data.put(PREDECESSORS, p);
        }
        NodeInfo nodeInfo = new NodeInfo(node);
        nodeInfo.appendNodeInfo(data);

        return data;
    }

    private String getErrorMessage(FlowNode node) {
        String errorMessage = null;
        ErrorAction error = node.getError();
        if (error != null) {
            errorMessage = error.getError().getMessage();
        }
        return errorMessage;
    }

    private String getStatus(FlowNode node) {
        if (node instanceof FlowEndNode) {
            return ((FlowEndNode) node).getResult().toString();
        }

        // Identify skipped stages, similar to BlueOcean
        // (https://github.com/jenkinsci/blueocean-plugin/blob/07bbd5082d314c215c4b750f337f20b88b19b3fa/blueocean-pipeline-api-impl/src/main/java/io/jenkins/blueocean/rest/impl/pipeline/PipelineNodeUtil.java#L95)
        if (StageStatus.isSkippedStage(node))
            return Result.NOT_BUILT.toString();
        if (node instanceof BlockEndNode && StageStatus.isSkippedStage(((BlockEndNode<?>) node).getStartNode()))
            return Result.NOT_BUILT.toString();

        ErrorAction error = node.getError();
        WarningAction warning = node.getPersistentAction(WarningAction.class);
        if (error != null) {
            if (error.getError() instanceof FlowInterruptedException) {
                return ((FlowInterruptedException) error.getError()).getResult().toString();
            } else {
                return Result.FAILURE.toString();
            }
        } else if (warning != null) {
            return warning.getResult().toString();
        }

        return Result.SUCCESS.toString();
    }

}
