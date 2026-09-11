package com.safeguard.agent.legal;

import com.safeguard.agent.legal.parser.LegalNumberNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegalNumberNormalizerTest {
    @Test
    void shouldCanonicalizeFullWidthDotAndWhitespace() {
        assertEquals("3.1.1", LegalNumberNormalizer.canonical("3 .1 ． 1"));
    }
}
