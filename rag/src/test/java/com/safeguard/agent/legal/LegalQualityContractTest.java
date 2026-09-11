package com.safeguard.agent.legal;

import com.safeguard.agent.legal.enums.LegalQualityStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegalQualityContractTest {
    @Test
    void shouldUseOnlyPassReviewFailedStatuses() {
        assertEquals(3, LegalQualityStatus.values().length);
    }
}
