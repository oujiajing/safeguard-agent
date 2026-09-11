package com.safeguard.agent.rag.controller.request;

import lombok.Data;

/**
 * 单个槽位提示词保存请求，内容留空即恢复回落内置智能体
 */
@Data
public class AgentPromptSaveRequest {

    private String content;
}
