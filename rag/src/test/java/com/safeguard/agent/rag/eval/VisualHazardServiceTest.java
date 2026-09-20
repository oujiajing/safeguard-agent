package com.safeguard.agent.rag.eval;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualHazardServiceTest {
    @Test
    void dropsHarnessClaimBasedOnlyOnNotSeeingIt() {
        assertTrue(VisualHazardService.isUnsupportedHarnessAbsence("未佩戴安全带", "工人未见佩戴安全带", List.of("未见安全带")));
    }

    @Test
    void keepsVisibleHarnessMisuse() {
        assertFalse(VisualHazardService.isUnsupportedHarnessAbsence("安全带未系挂", "安全带挂钩未连接挂点", List.of("可见安全带挂钩未连接")));
    }
}
