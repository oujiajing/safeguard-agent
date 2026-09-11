package com.safeguard.agent.knowledge.controller.request;

import lombok.Data;

@Data
public class KnowledgeDocumentUploadRequest {

    /**
     * 领域处理策略：GENERAL / LEGAL。缺省为 GENERAL。
     */
    private String processingStrategy;

    /**
     * 来源类型：file / url
     */
    private String sourceType;

    /**
     * 来源位置（URL）
     */
    private String sourceLocation;

    /**
     * 是否开启定时拉取
     */
    private Boolean scheduleEnabled;

    /**
     * 定时表达式（cron）
     */
    private String scheduleCron;

    /**
     * 处理模式：chunk / pipeline
     * - chunk: 使用分块策略直接分块
     * - pipeline: 使用数据通道进行清洗处理
     */
    private String processMode;

    /**
     * 文档级摄取配置 JSON，仅在 processMode=chunk 时有效
     * <p>
     * 形如 {@code {"parseProfile":"fast","maxChars":512,"overlapChars":64,"rowsPerChunk":50}}，
     * 缺省走系统默认预算
     */
    private String ingestionSpec;

    /**
     * 数据通道（Pipeline）ID
     * 仅在 processMode=pipeline 时有效
     */
    private String pipelineId;
}
