package com.safeguard.agent.ingestion.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.framework.exception.ClientException;
import com.safeguard.agent.ingestion.domain.context.IngestionContext;
import com.safeguard.agent.ingestion.domain.enums.IngestionStatus;
import com.safeguard.agent.ingestion.domain.pipeline.NodeConfig;
import com.safeguard.agent.ingestion.domain.pipeline.PipelineDefinition;
import com.safeguard.agent.ingestion.domain.result.NodeResult;
import com.safeguard.agent.ingestion.node.IngestionNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestionEngineTest {

    @Test
    void multipleStartNodesFailBeforeAnyNodeExecutes() {
        List<String> executedNodeIds = new ArrayList<>();
        IngestionEngine engine = engine(executedNodeIds);
        PipelineDefinition pipeline = PipelineDefinition.builder()
                .nodes(List.of(
                        node("z-root", "z-leaf"),
                        node("a-leaf", null),
                        node("z-leaf", null),
                        node("a-root", "a-leaf")))
                .build();

        ClientException exception = assertThrows(
                ClientException.class,
                () -> engine.execute(pipeline, IngestionContext.builder().build()));

        assertEquals("流水线存在多个起始节点: a-root, z-root", exception.getMessage());
        assertEquals(List.of(), executedNodeIds);
    }

    @Test
    void nullStartNodeIdAlongsideNormalRootFailsWithoutNpe() {
        List<String> executedNodeIds = new ArrayList<>();
        IngestionEngine engine = engine(executedNodeIds);
        PipelineDefinition pipeline = PipelineDefinition.builder()
                .nodes(List.of(
                        node("a-root", null),
                        node(null, null)))
                .build();

        ClientException exception = assertThrows(
                ClientException.class,
                () -> engine.execute(pipeline, IngestionContext.builder().build()));

        assertEquals("流水线存在多个起始节点: null, a-root", exception.getMessage());
        assertEquals(List.of(), executedNodeIds);
    }

    @Test
    void blankStartNodeIdIsRejectedBeforeExecution() {
        List<String> executedNodeIds = new ArrayList<>();
        IngestionEngine engine = engine(executedNodeIds);
        PipelineDefinition pipeline = PipelineDefinition.builder()
                .nodes(List.of(node(" ", null)))
                .build();

        ClientException exception = assertThrows(
                ClientException.class,
                () -> engine.execute(pipeline, IngestionContext.builder().build()));

        assertEquals("流水线未找到起始节点", exception.getMessage());
        assertEquals(List.of(), executedNodeIds);
    }

    @Test
    void singleChainStillExecutesEveryNodeInOrder() {
        List<String> executedNodeIds = new ArrayList<>();
        IngestionEngine engine = engine(executedNodeIds);
        PipelineDefinition pipeline = PipelineDefinition.builder()
                .nodes(List.of(
                        node("middle", "leaf"),
                        node("leaf", null),
                        node("root", "middle")))
                .build();
        IngestionContext context = IngestionContext.builder().build();

        engine.execute(pipeline, context);

        assertEquals(List.of("root", "middle", "leaf"), executedNodeIds);
        assertEquals(IngestionStatus.COMPLETED, context.getStatus());
    }

    private IngestionEngine engine(List<String> executedNodeIds) {
        IngestionNode node = new IngestionNode() {
            @Override
            public String getNodeType() {
                return "test";
            }

            @Override
            public NodeResult execute(IngestionContext context, NodeConfig config) {
                executedNodeIds.add(config.getNodeId());
                return NodeResult.ok();
            }
        };
        return new IngestionEngine(
                List.of(node),
                new ConditionEvaluator(new ObjectMapper()),
                new NodeOutputExtractor());
    }

    private NodeConfig node(String nodeId, String nextNodeId) {
        return NodeConfig.builder()
                .nodeId(nodeId)
                .nodeType("test")
                .nextNodeId(nextNodeId)
                .build();
    }
}
