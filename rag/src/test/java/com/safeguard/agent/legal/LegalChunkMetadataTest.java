package com.safeguard.agent.legal;

import com.safeguard.agent.legal.enums.LegalChunkType;
import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.model.LegalChunkMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegalChunkMetadataTest {
    @Test
    void shouldSerializeCitationFactsToStableMap() {
        var metadata = new LegalChunkMetadata("d", "标题", "JGJ 1-2020", "1", "总则", null, null,
                "1.0.1", "1 总则 / 1.0.1", "c", null, LegalContentRole.NORMATIVE,
                LegalChunkType.CLAUSE, null, null);
        assertEquals("1.0.1", metadata.toMap().get("clause_no"));
        assertEquals("c", metadata.toMap().get("parent_clause_id"));
    }
}
