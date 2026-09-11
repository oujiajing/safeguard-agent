package com.safeguard.agent.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.legal.persistence.LegalCorpusPersistenceService;
import com.safeguard.agent.legal.persistence.LegalPersistenceResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalCorpusPersistenceIdempotencyTest {

    @Test
    void returnsAlreadyImportedForTheSameOriginalBytesAndParserVersion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of("existing-document"));

        LegalCorpusPersistenceService service = new LegalCorpusPersistenceService(
                jdbc, new ObjectMapper(), LegalTestFixtures.importer());
        LegalPersistenceResult result = service.importText(
                "sample.pdf", "第一条 文本。".getBytes(StandardCharsets.UTF_8));

        assertEquals("ALREADY_IMPORTED", result.status());
        assertEquals("existing-document", result.documentId());
        assertEquals(0, result.chunkCount());
        org.mockito.Mockito.verify(jdbc).query(anyString(), any(RowMapper.class), any(), any(), any());
    }
}
