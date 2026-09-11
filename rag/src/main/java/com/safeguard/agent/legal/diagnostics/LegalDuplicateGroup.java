package com.safeguard.agent.legal.diagnostics;

import com.safeguard.agent.legal.enums.LegalContentRole;

import java.util.List;

public record LegalDuplicateGroup(
        String document,
        LegalContentRole contentRole,
        String clauseNo,
        LegalDuplicateType duplicateType,
        LegalDuplicateType duplicateOrigin,
        int duplicateClauseCount,
        List<String> textPreviews
) {
    public LegalDuplicateGroup {
        textPreviews = textPreviews == null ? List.of() : List.copyOf(textPreviews);
    }
}
