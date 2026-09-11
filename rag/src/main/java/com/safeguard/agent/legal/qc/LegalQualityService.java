package com.safeguard.agent.legal.qc;

import com.safeguard.agent.legal.config.LegalIngestionProperties;
import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.enums.LegalQualityStatus;
import com.safeguard.agent.legal.enums.LegalStructureType;
import com.safeguard.agent.legal.model.LegalChunk;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalDocumentElement;
import com.safeguard.agent.legal.model.LegalQualityReport;
import com.safeguard.agent.legal.model.NormalizedLegalDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LegalQualityService {

    private final LegalIngestionProperties properties;

    public LegalQualityService(LegalIngestionProperties properties) {
        this.properties = properties;
    }

    public LegalQualityReport assess(NormalizedLegalDocument document, List<LegalChunk> chunks) {
        List<String> warnings = new ArrayList<>(document.warnings());
        int parsedTextLength = document.elements().stream().mapToInt(e -> e.normalizedText().length()).sum();
        int chapterCount = countType(document.elements(), LegalStructureType.CHAPTER);
        int sectionCount = countType(document.elements(), LegalStructureType.SECTION);
        int normative = countClauses(document.clauses(), LegalContentRole.NORMATIVE);
        int commentary = countClauses(document.clauses(), LegalContentRole.COMMENTARY);
        int supplementary = countRole(document.elements(), LegalContentRole.SUPPLEMENTARY);
        int appendix = countRole(document.elements(), LegalContentRole.APPENDIX);
        int unknown = countRole(document.elements(), LegalContentRole.UNKNOWN);
        int duplicates = duplicateClauseCount(document.clauses());
        int empty = (int) chunks.stream().filter(c -> c.content().isBlank()).count();
        int oversized = (int) chunks.stream()
                .filter(c -> c.tokenCount() > properties.getChunk().getHardLimitTokens()).count();
        int unstructured = document.unstructuredParagraphs().size();

        boolean failed = parsedTextLength == 0 || document.clauses().isEmpty() || chunks.isEmpty() || empty > 0;
        boolean review = false;
        if (oversized > 0) {
            review = true;
            warnings.add("存在 " + oversized + " 个超过 hardLimitTokens 的 chunk");
        }
        if (duplicates > 0) {
            review = true;
            warnings.add("存在 " + duplicates + " 个同 (contentRole, clauseNo) 重复条款");
        }
        double unstructuredRatio = ratio(unstructured, document.elements().size());
        if (unstructuredRatio > properties.getQuality().getMaxUnstructuredRatio()) {
            review = true;
            warnings.add(String.format("未结构化段落比例 %.2f%% 超过阈值", unstructuredRatio * 100));
        }
        double unknownRatio = ratio(unknown, document.elements().size());
        if (unknownRatio > properties.getQuality().getMaxUnknownRoleRatio()) {
            review = true;
            warnings.add(String.format("UNKNOWN contentRole 比例 %.2f%% 超过阈值", unknownRatio * 100));
        }
        if (warnings.stream().anyMatch(w -> w.contains("冲突"))) review = true;
        if (warnings.stream().anyMatch(w -> w.startsWith("PDF_"))) review = true;

        LegalQualityStatus status = failed ? LegalQualityStatus.FAILED
                : review ? LegalQualityStatus.REVIEW : LegalQualityStatus.PASS;
        return new LegalQualityReport(
                document.metadata().documentId(), null, countType(document.elements(), LegalStructureType.TABLE),
                parsedTextLength, chapterCount, sectionCount, document.clauses().size(),
                normative, commentary, supplementary, appendix, unknown, unstructured,
                duplicates, chunks.size(), oversized, empty, status, warnings);
    }

    private int duplicateClauseCount(List<LegalClause> clauses) {
        Map<String, Integer> counts = new HashMap<>();
        for (LegalClause clause : clauses) {
            counts.merge(clause.contentRole() + "|" + clause.clauseNo(), 1, Integer::sum);
        }
        return counts.values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
    }

    private int countType(List<LegalDocumentElement> elements, LegalStructureType type) {
        return (int) elements.stream().filter(e -> e.structureType() == type).count();
    }

    private int countRole(List<LegalDocumentElement> elements, LegalContentRole role) {
        return (int) elements.stream().filter(e -> e.contentRole() == role).count();
    }

    private int countClauses(List<LegalClause> clauses, LegalContentRole role) {
        return (int) clauses.stream().filter(c -> c.contentRole() == role).count();
    }

    private double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0 : (double) numerator / denominator;
    }
}
