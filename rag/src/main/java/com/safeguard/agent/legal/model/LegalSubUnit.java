package com.safeguard.agent.legal.model;

import com.safeguard.agent.legal.enums.LegalStructureType;

public record LegalSubUnit(
        LegalStructureType structureType,
        String marker,
        String rawText,
        String normalizedText,
        int elementIndex
) {
    public LegalSubUnit {
        structureType = structureType == null ? LegalStructureType.PARAGRAPH : structureType;
        rawText = rawText == null ? "" : rawText;
        normalizedText = normalizedText == null ? "" : normalizedText;
    }
}
