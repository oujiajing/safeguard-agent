package com.safeguard.agent.legal;

import com.safeguard.agent.legal.metadata.LegalMetadataExtractor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegalMetadataExtractorTest {
    @Test
    void shouldNormalizeStandardNumberFromFilename() {
        var result = new LegalMetadataExtractor().extract("doc-1", "规范 GBT 50326-2017.txt",
                "规范\n".getBytes(StandardCharsets.UTF_8), "规范\n", "test/1", "hash");
        assertEquals("GB/T 50326-2017", result.metadata().standardNo());
    }
}
