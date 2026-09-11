package com.safeguard.agent.legal;

import com.safeguard.agent.legal.enums.LegalQualityStatus;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalQualityServiceTest {

    @Test
    void shouldFailDocumentWithoutClause() {
        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000004", "无条款文件.txt",
                "只有一段无法识别的正文。".getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);
        assertEquals(LegalQualityStatus.FAILED, result.qualityReport().qualityStatus());
        assertEquals(0, result.qualityReport().clauseCount());
    }

    @Test
    void shouldReviewDuplicateClauseWithinSameRole() {
        String text = """
                建筑施工安全检查标准
                1 总则
                1.0.1 第一份正文。
                1.0.1 第二份同号正文。
                """;
        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000005", "普通标准.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);
        assertEquals(LegalQualityStatus.REVIEW, result.qualityReport().qualityStatus());
        assertEquals(1, result.qualityReport().duplicateClauseCount());
        assertTrue(result.qualityReport().warnings().stream().anyMatch(w -> w.contains("重复条款")));
    }
}
