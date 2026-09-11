package com.safeguard.agent.rag.core.vector;

import com.safeguard.agent.framework.convention.RetrievedChunk;
import com.safeguard.agent.infra.embedding.EmbeddingService;
import com.safeguard.agent.rag.core.retrieval.RetrieveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorRetrieverServiceTest {

    @Test
    @DisplayName("多Collection使用单条IN查询并只携带一个总LIMIT")
    @SuppressWarnings("unchecked")
    void queryMultipleCollectionsWithOneSharedLimit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed("报销流程")).thenReturn(List.of(3.0F, 4.0F));
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(List.<RetrievedChunk>of());

        PgVectorRetrieverService service = new PgVectorRetrieverService(jdbcTemplate, embeddingService);
        service.retrieve(RetrieveRequest.builder()
                .query("报销流程")
                .collectionNames(List.of("kb-finance", "kb-policy"))
                .topK(7)
                .build());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(1)).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                argsCaptor.capture()
        );

        assertTrue(sqlCaptor.getValue().contains("collection_name IN (?, ?)"));
        Object[] args = argsCaptor.getValue();
        assertEquals("kb-finance", args[1]);
        assertEquals("kb-policy", args[2]);
        assertEquals(7, args[4], "SQL 只能有一个跨 Collection 共享的 LIMIT");
        verify(embeddingService, times(1)).embed("报销流程");
    }
}
