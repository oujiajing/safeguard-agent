package com.safeguard.agent.ingestion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.audit.support.BizChangeLogContext;
import com.safeguard.agent.ingestion.controller.request.IngestionPipelineNodeRequest;
import com.safeguard.agent.ingestion.controller.request.IngestionPipelineUpdateRequest;
import com.safeguard.agent.ingestion.dao.entity.IngestionPipelineDO;
import com.safeguard.agent.ingestion.dao.entity.IngestionPipelineNodeDO;
import com.safeguard.agent.ingestion.dao.mapper.IngestionPipelineMapper;
import com.safeguard.agent.ingestion.dao.mapper.IngestionPipelineNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionPipelineServiceImplTest {

    @Mock
    private IngestionPipelineMapper pipelineMapper;

    @Mock
    private IngestionPipelineNodeMapper nodeMapper;

    @Mock
    private BizChangeLogContext bizChangeLogContext;

    private IngestionPipelineServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IngestionPipelineServiceImpl(
                pipelineMapper,
                nodeMapper,
                new ObjectMapper(),
                bizChangeLogContext
        );
    }

    @Test
    void updatePhysicallyDeletesExistingNodesBeforeReinsertingThem() {
        String pipelineId = "pipeline-1";
        IngestionPipelineDO pipeline = IngestionPipelineDO.builder()
                .id(pipelineId)
                .name("default")
                .build();
        when(pipelineMapper.selectById(pipelineId)).thenReturn(pipeline);
        when(nodeMapper.selectList(any())).thenReturn(List.of());

        IngestionPipelineNodeRequest node = new IngestionPipelineNodeRequest();
        node.setNodeId("fetcher-1");
        node.setNodeType("fetcher");
        IngestionPipelineUpdateRequest request = new IngestionPipelineUpdateRequest();
        request.setNodes(List.of(node));

        service.update(pipelineId, request);

        InOrder order = inOrder(nodeMapper);
        order.verify(nodeMapper).physicalDeleteByPipelineId(pipelineId);
        order.verify(nodeMapper).insert(argThat((IngestionPipelineNodeDO inserted) ->
                pipelineId.equals(inserted.getPipelineId())
                        && "fetcher-1".equals(inserted.getNodeId())
        ));
    }
}
