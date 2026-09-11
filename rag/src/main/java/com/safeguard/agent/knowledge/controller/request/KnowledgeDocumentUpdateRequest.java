package com.safeguard.agent.knowledge.controller.request;

import lombok.Data;

@Data
public class KnowledgeDocumentUpdateRequest {

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 处理模式：chunk / pipeline
     */
    private String processMode;


    /**
     * 摄取配置 JSON（CHUNK 模式），如 {"parseProfile":"fast","maxChars":1024,"overlapChars":128}，
     * 字段可缺省，落库前由 IngestionSpecCodec 校验并归一化
     */
    private String ingestionSpec;

    /**
     * Pipeline ID（PIPELINE 模式）
     */
    private String pipelineId;

    /**
     * 来源位置（URL）
     */
    private String sourceLocation;

    /**
     * 是否开启定时拉取：1-启用，0-禁用
     */
    private Integer scheduleEnabled;

    /**
     * 定时表达式（cron）
     */
    private String scheduleCron;
}
