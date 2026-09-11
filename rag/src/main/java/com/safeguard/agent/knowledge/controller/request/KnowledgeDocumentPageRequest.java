package com.safeguard.agent.knowledge.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

@Data
public class KnowledgeDocumentPageRequest extends Page {

    private String status;

    private String keyword;
}
