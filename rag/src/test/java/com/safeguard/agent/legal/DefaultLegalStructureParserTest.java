package com.safeguard.agent.legal;

import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultLegalStructureParserTest {
    @Test
    void shouldClassifyRoleHeadingsDeterministically() {
        var result = LegalTestFixtures.importer().importText("doc-role", "《规范》JGJ 1-2020.txt",
                ("1 总则\n1.0.1 正文。\n本规范用词说明\n1 表示必须。\n条文说明\n1.0.1 解释。\n").getBytes(StandardCharsets.UTF_8),
                CleanedTextImportMode.DRY_RUN);
        assertEquals(LegalContentRole.COMMENTARY, result.document().clauses().get(1).contentRole());
    }
}
