package com.safeguard.agent.rag.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class HostedRectificationServiceTest {
  private final VisualHazardService visualService = mock(VisualHazardService.class);
  private final HostedRectificationService service = new HostedRectificationService(visualService);
  private final SafeGuardExecutionContext context = new SafeGuardExecutionContext(1L, 2L, 3L, 4L, "quick-shot:9", "trace-1");

  @Test
  void missingAfterEvidenceNeverSuggestsAcceptance() {
    var result = service.review(new HostedRectificationReviewRequest("order-1", 2,
        List.of(new HostedRectificationReviewRequest.Criterion("防护栏杆连续、牢固")), List.of("整改前可见缺口"), List.of(), "已整改", context));
    assertThat(result.overallSuggestion()).isEqualTo("NEED_MORE_INFORMATION");
    assertThat(result.criterionResults().get(0).result()).isEqualTo("NOT_OBSERVABLE");
  }

  @Test
  void summaryIsDraftAndCitesBusinessFacts() {
    var result = service.summary(new HostedRectificationSummaryRequest("order-1", 7, "临边缺少防护", "已补充防护栏杆", "PASS", "验收合格", List.of("补充防护栏杆"), List.of("连续牢固"), List.of("ACCEPT"), List.of("order.item.1"), context));
    assertThat(result.status()).isEqualTo("DRAFT");
    assertThat(result.sections()).hasSize(3);
    assertThat(result.citations()).containsExactly("order.item.1");
  }

  @Test
  void analyzesInlineRealImageEvidenceAndNeverSuggestsWithoutIt() {
    String png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    org.mockito.Mockito.when(visualService.analyze(org.mockito.ArgumentMatchers.any())).thenReturn(
        new VisualHazardContext("vision-1", "现场", "", "test-vlm", java.time.Instant.now(), List.of()));
    var result = service.review(new HostedRectificationReviewRequest("order-image", 1,
        List.of(new HostedRectificationReviewRequest.Criterion("整改后现场")), List.of(), List.of(), "已整改", context,
        List.of(), List.of(png)));
    assertThat(result.status()).isEqualTo("REVIEWED");
    assertThat(result.criterionResults().get(0).evidence().get(0)).startsWith("整改后 image:");
    assertThat(result.requiresManualReview()).isTrue();
  }

  @Test
  void rejectsRemoteImageReferencesInsteadOfFetchingArbitraryUrls() {
    var request = new HostedRectificationReviewRequest("order-image", 1,
        List.of(new HostedRectificationReviewRequest.Criterion("整改后现场")), List.of(), List.of(), "已整改", context,
        List.of(), List.of("https://example.com/photo.png"));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.review(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("data:image");
  }

  @Test
  void visionFailureDegradesToManualReviewWithoutAcceptSuggestion() {
    String png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    org.mockito.Mockito.when(visualService.analyze(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("VLM timeout"));
    var result = service.review(new HostedRectificationReviewRequest("order-timeout", 1,
        List.of(new HostedRectificationReviewRequest.Criterion("整改后现场")), List.of(), List.of(), "已整改", context,
        List.of(), List.of(png)));
    assertThat(result.status()).isEqualTo("NEED_MORE_INFORMATION");
    assertThat(result.overallSuggestion()).isEqualTo("NEED_MORE_INFORMATION");
    assertThat(result.criterionResults().get(0).result()).isEqualTo("UNCERTAIN");
  }
}
