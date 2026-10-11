package com.safeguard.agent.rag.eval;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.framework.config.SafeGuardServiceProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HostedRectificationControllerSecurityTest {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    SafeGuardServiceProperties properties = new SafeGuardServiceProperties();
    properties.setToken("service-secret");
    mvc = MockMvcBuilders.standaloneSetup(new HostedHazardController(
        mock(HostedHazardService.class), mock(HostedRectificationService.class), properties)).build();
  }

  @Test
  void rejectsMissingAndWrongServiceTokensBeforeReadingRectificationPayload() throws Exception {
    String payload = new ObjectMapper().writeValueAsString(Map.of("orderId", "1", "orderVersion", 1,
        "criteria", java.util.List.of(Map.of("text", "护栏连续"))));
    mvc.perform(post("/agent/v1/hosted/rectification-reviews").contentType("application/json").content(payload))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/agent/v1/hosted/rectification-reviews").header("X-Safeguard-Service-Token", "wrong")
        .contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
  }
}
