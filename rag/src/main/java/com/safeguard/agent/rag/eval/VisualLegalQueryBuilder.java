package com.safeguard.agent.rag.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a human-confirmed visual candidate into a constrained legal-retrieval query.
 * Confidence remains visual provenance; it is never represented as legal relevance.
 */
public final class VisualLegalQueryBuilder {

    private VisualLegalQueryBuilder() {
    }

    public static VisualLegalQuery build(String hazardType, String operationObject, String confirmedDescription,
                                         List<String> visibleEvidence, String potentialRisk, String judgement,
                                         double confidence, boolean needsManualVerification) {
        List<String> facts = new ArrayList<>();
        append(facts, "隐患类型", hazardType);
        append(facts, "作业对象", operationObject);
        append(facts, "已确认现场描述", confirmedDescription);
        if (visibleEvidence != null) {
            visibleEvidence.stream().filter(item -> item != null && !item.isBlank())
                    .map(String::trim).distinct().forEach(item -> facts.add("可见事实：" + item));
        }
        append(facts, "潜在风险", potentialRisk);

        String query = "建筑施工现场隐患法规检索。" + String.join("；", facts)
                + "。请仅依据上述已确认事实，检索直接适用的施工安全法规条款及整改要求；"
                + "不得补充图片中不可见的尺寸、检测值、证件、荷载或人员防护状态。";
        return new VisualLegalQuery(query, List.copyOf(facts), safe(judgement), clamp(confidence), needsManualVerification);
    }

    private static void append(List<String> facts, String label, String value) {
        if (value != null && !value.isBlank()) {
            facts.add(label + "：" + value.trim());
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }

    private static double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    public record VisualLegalQuery(String query, List<String> confirmedFacts,
                                   String visualJudgement, double visualConfidence,
                                   boolean needsManualVerification) {
    }
}
