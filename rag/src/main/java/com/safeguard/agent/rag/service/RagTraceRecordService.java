package com.safeguard.agent.rag.service;

import com.safeguard.agent.rag.dao.entity.RagTraceNodeDO;
import com.safeguard.agent.rag.dao.entity.RagTraceRunDO;

import java.util.Date;

/**
 * RAG Trace 记录服务
 */
public interface RagTraceRecordService {

    void startRun(RagTraceRunDO run);

    void finishRun(String traceId, String status, String errorMessage, Date endTime, long durationMs);

    void startNode(RagTraceNodeDO node);

    void finishNode(String traceId, String nodeId, String status, String errorMessage, Date endTime, long durationMs);
}
