package com.safeguard.agent.rag.core.source;

import com.safeguard.agent.framework.convention.SourceRef;
import com.safeguard.agent.rag.config.RAGConfigProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationContextEnricherTest {

    private final CitationContextEnricher enricher = enricher(true);

    private static CitationContextEnricher enricher(boolean citationEnabled) {
        RAGConfigProperties properties = new RAGConfigProperties();
        properties.setCitationEnabled(citationEnabled);
        return new CitationContextEnricher(properties);
    }

    @Test
    void injectsSharedSourceIndexesAndRemovesInternalDocumentIds() {
        String context = """
                <content data-safeguard-doc-id="doc-a">
                A
                </content>
                <content data-safeguard-doc-id="doc-b">
                B
                </content>
                <content data-safeguard-doc-id="doc-a">
                A2
                </content>
                """;
        List<SourceRef> sources = List.of(
                SourceRef.builder().index(1).docId("doc-b").build(),
                SourceRef.builder().index(2).docId("doc-a").build()
        );

        String result = enricher.enrich(context, sources);

        // 同一文档的多个块复用同一编号
        assertEquals(2, result.split("<content ref=\"2\">", -1).length - 1);
        assertTrue(result.contains("<content ref=\"1\">"));
        assertFalse(result.contains("data-safeguard-doc-id"));
    }

    @Test
    void removesInternalIdWhenSourceWasNotRegistered() {
        String context = """
                <content data-safeguard-doc-id="doc-x">
                X
                </content>
                """;

        String result = enricher.enrich(context, List.of());

        assertTrue(result.contains("<content>"));
        assertFalse(result.contains("data-safeguard-doc-id"));
        assertFalse(result.contains(" ref="));
    }

    @Test
    void stripsInternalIdWithoutNumberingWhenCitationDisabled() {
        String context = """
                <content data-safeguard-doc-id="doc-a">
                A
                </content>
                """;
        List<SourceRef> sources = List.of(SourceRef.builder().index(1).docId("doc-a").build());

        String result = enricher(false).enrich(context, sources);

        // 关闭引用：有来源也不注入编号，但内部 docId 仍必须抹掉
        assertTrue(result.contains("<content>"));
        assertFalse(result.contains("data-safeguard-doc-id"));
        assertFalse(result.contains(" ref="));
    }
}
