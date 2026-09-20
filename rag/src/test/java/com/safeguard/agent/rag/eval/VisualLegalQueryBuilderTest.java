package com.safeguard.agent.rag.eval;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualLegalQueryBuilderTest {

    @Test
    void preservesConfirmedVisualFactsInTheLegalQuery() {
        VisualLegalQueryBuilder.VisualLegalQuery query = VisualLegalQueryBuilder.build(
                "临边防护缺失", "楼梯口", "楼梯口临边未设置连续防护栏杆",
                List.of("二层楼梯口边缘可见", "未见连续防护栏杆"), "高处坠落", "CONFIRMED", .88D, true);

        assertTrue(query.query().contains("隐患类型：临边防护缺失"));
        assertTrue(query.query().contains("作业对象：楼梯口"));
        assertTrue(query.query().contains("可见事实：二层楼梯口边缘可见"));
        assertTrue(query.query().contains("潜在风险：高处坠落"));
        assertTrue(query.query().contains("不得补充图片中不可见的尺寸"));
        assertEquals("CONFIRMED", query.visualJudgement());
        assertEquals(.88D, query.visualConfidence());
        assertTrue(query.needsManualVerification());
    }

    @Test
    void doesNotInventMissingFieldsOrUseConfidenceAsLegalRelevance() {
        VisualLegalQueryBuilder.VisualLegalQuery query = VisualLegalQueryBuilder.build(
                "安全帽佩戴", null, "人员未正确佩戴安全帽", List.of(), null,
                null, 2D, false);

        assertFalse(query.query().contains("作业对象："));
        assertFalse(query.query().contains("潜在风险："));
        assertFalse(query.query().contains("置信度"));
        assertEquals("UNKNOWN", query.visualJudgement());
        assertEquals(1D, query.visualConfidence());
    }
}
