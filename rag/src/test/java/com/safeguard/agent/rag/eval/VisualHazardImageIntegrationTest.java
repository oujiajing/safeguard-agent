package com.safeguard.agent.rag.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VisualHazardImageIntegrationTest {
  @Test
  void sendsRealImageBytesToVisionModelAndParsesSemanticCandidate() throws Exception {
    AtomicReference<String> body = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/api/chat", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"message\":{\"content\":\"{\\\"scene\\\":\\\"整改现场\\\",\\\"hazardCandidates\\\":[]}\"}}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      VisualHazardProperties properties = new VisualHazardProperties();
      properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
      VisualHazardService service = new VisualHazardService(properties, new ObjectMapper(), mock(HazardAssessmentService.class));
      String image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
      var result = service.analyze(new VisualHazardAnalysisRequest("整改后验收", image,
          new SafeGuardExecutionContext(1L, 2L, 3L, 4L, "rectification:1", "trace-image")));
      assertThat(result.scene()).isEqualTo("整改现场");
      assertThat(body.get()).contains(image.substring(image.indexOf(',') + 1));
    } finally {
      server.stop(0);
    }
  }
}
