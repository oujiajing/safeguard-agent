package com.safeguard.agent.legal;

import com.safeguard.agent.legal.clean.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalCleaningPipelineTest {
    @Test
    void shouldRetainRawLineAndNormalizeNumberWhitespace() {
        var pipeline = new LegalCleaningPipeline(java.util.List.of(new UnicodeNormalizationStep(),
                new WhitespaceNormalizationStep(), new LegalNumberWhitespaceNormalizationStep(), new EmptyElementCleanupStep()));
        var elements = pipeline.clean("doc", "3 .1 .1  要求\n\n");
        assertEquals(1, elements.size());
        assertTrue(elements.get(0).rawText().startsWith("3 .1 .1"));
        assertEquals("3.1.1 要求", elements.get(0).normalizedText());
    }
}
