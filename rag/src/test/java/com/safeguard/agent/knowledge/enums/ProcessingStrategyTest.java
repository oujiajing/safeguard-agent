package com.safeguard.agent.knowledge.enums;

import com.safeguard.agent.framework.exception.ClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingStrategyTest {

    @Test
    void defaultsToGeneralForOldClients() {
        assertEquals(ProcessingStrategy.GENERAL, ProcessingStrategy.normalize(null));
        assertEquals(ProcessingStrategy.GENERAL, ProcessingStrategy.normalize("  "));
    }

    @Test
    void acceptsKnownStrategiesCaseInsensitively() {
        assertEquals(ProcessingStrategy.LEGAL, ProcessingStrategy.normalize("legal"));
        assertEquals(ProcessingStrategy.GENERAL, ProcessingStrategy.normalize("GENERAL"));
    }

    @Test
    void rejectsUnknownStrategyInsteadOfFallingBack() {
        assertThrows(ClientException.class, () -> ProcessingStrategy.normalize("guess"));
    }
}
