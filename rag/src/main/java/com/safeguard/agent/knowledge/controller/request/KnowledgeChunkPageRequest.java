package com.safeguard.agent.knowledge.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

@Data
public class KnowledgeChunkPageRequest extends Page {

    private Integer enabled;
    private String chapterNo;
    private String reviewStatus;
}
