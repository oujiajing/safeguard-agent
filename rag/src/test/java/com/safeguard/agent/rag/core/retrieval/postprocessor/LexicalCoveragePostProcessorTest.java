package com.safeguard.agent.rag.core.retrieval.postprocessor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LexicalCoveragePostProcessorTest {
    @Test
    void requiresTwoSharedDomainAnchors() {
        assertEquals(2, LexicalCoveragePostProcessor.lexicalAnchorCount(
                "雨雪天气高处作业，安全带分别有什么要求", "高处作业时应系安全带并采取防滑措施"));
        assertEquals(1, LexicalCoveragePostProcessor.lexicalAnchorCount(
                "人员安全帽要求", "气瓶防振圈、安全帽应齐全良好"));
    }
}
