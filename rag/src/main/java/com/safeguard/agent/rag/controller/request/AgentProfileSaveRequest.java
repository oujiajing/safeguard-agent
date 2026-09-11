package com.safeguard.agent.rag.controller.request;

import lombok.Data;

/**
 * 智能体新建与改名请求
 */
@Data
public class AgentProfileSaveRequest {

    private String name;

    private String description;

    /**
     * 头像预设标识，由前端从预设表中挑选
     */
    private String avatar;
}
