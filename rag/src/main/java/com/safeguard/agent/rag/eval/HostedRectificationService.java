package com.safeguard.agent.rag.eval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class HostedRectificationService {
  private final VisualHazardService visualHazardService;

  public HostedRectificationService(VisualHazardService visualHazardService) {
    this.visualHazardService = visualHazardService;
  }

  public HostedRectificationReviewResponse review(HostedRectificationReviewRequest request) {
    requireContext(request.executionContext());
    List<String> after = new ArrayList<>(safe(request.afterEvidence()));
    VisualEvidence beforeVision = analyzeImages(request.beforeImageData(), "整改前", request, false);
    VisualEvidence afterVision = analyzeImages(request.afterImageData(), "整改后", request, true);
    after.addAll(afterVision.evidence());
    List<HostedRectificationReviewResponse.CriterionResult> results = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (HostedRectificationReviewRequest.Criterion criterion : request.criteria()) {
      String text = criterion.text().trim();
      if (!safe(request.afterImageData()).isEmpty() && !afterVision.succeeded()) {
        results.add(new HostedRectificationReviewResponse.CriterionResult(text, "UNCERTAIN", after, "视觉模型不可用，不能依据图片判断验收结果"));
        missing.add("请人工核验整改后图片：" + text);
      } else if (after.isEmpty()) {
        results.add(new HostedRectificationReviewResponse.CriterionResult(text, "NOT_OBSERVABLE", List.of(), "缺少整改后现场证据"));
        missing.add("整改后图片或视频：" + text);
      } else if (afterVision.confirmedRisk()) {
        results.add(new HostedRectificationReviewResponse.CriterionResult(text, "FAIL", after, "整改后图片仍识别到明确安全隐患"));
      } else if (afterVision.uncertainRisk()) {
        results.add(new HostedRectificationReviewResponse.CriterionResult(text, "UNCERTAIN", after, "整改后图片仍存在疑似或无法确认的安全现象"));
        missing.add("请人工核验图片中的疑似风险：" + text);
      } else if (afterVision.succeeded()) {
        results.add(new HostedRectificationReviewResponse.CriterionResult(text, "PASS", after, "视觉模型未在整改后图片中识别到剩余隐患，仍需人工最终确认"));
      } else if (containsEvidence(after, text)) {
        results.add(new HostedRectificationReviewResponse.CriterionResult(text, "PASS", after, "整改后证据与验收标准存在直接文本对应"));
      } else {
        results.add(new HostedRectificationReviewResponse.CriterionResult(text, "UNCERTAIN", after, "现有证据不足以确认该验收标准"));
        missing.add("补充能证明以下标准的现场证据：" + text);
      }
    }
    boolean allPass = !results.isEmpty() && results.stream().allMatch(item -> "PASS".equals(item.result()));
    String suggestion = allPass ? "SUGGEST_ACCEPT" : (after.isEmpty() || !afterVision.succeeded() ? "NEED_MORE_INFORMATION" : "SUGGEST_REJECT");
    String status = after.isEmpty() || (!safe(request.afterImageData()).isEmpty() && !afterVision.succeeded()) ? "NEED_MORE_INFORMATION" : "REVIEWED";
    return new HostedRectificationReviewResponse(UUID.randomUUID().toString(), request.orderId(), request.orderVersion(), status,
        digest(request), List.copyOf(results), List.of(), List.copyOf(missing), suggestion, allPass ? 0.86 : 0.42, true,
        List.of(new HostedRectificationReviewResponse.TraceStep("VISION_BEFORE", "整改前图片语义分析", beforeVision.succeeded()),
            new HostedRectificationReviewResponse.TraceStep("VISION_AFTER", "整改后图片语义分析", afterVision.succeeded()),
            new HostedRectificationReviewResponse.TraceStep("COMPARE_EVIDENCE", "对比整改前后现场证据", true),
            new HostedRectificationReviewResponse.TraceStep("RESULT", suggestion, true)));
  }

  public HostedRectificationSummaryResponse summary(HostedRectificationSummaryRequest request) {
    requireContext(request.executionContext());
    List<HostedRectificationSummaryResponse.Section> sections = List.of(
        new HostedRectificationSummaryResponse.Section("hazard", "隐患概述", text(request.hazardDescription(), "未提供隐患描述"), List.of("hazardDescription")),
        new HostedRectificationSummaryResponse.Section("rectification", "整改措施", text(request.rectificationDescription(), join(request.measures())), List.of("rectificationDescription", "measures")),
        new HostedRectificationSummaryResponse.Section("acceptance", "验收结论", text(request.acceptanceRemark(), text(request.acceptanceResult(), "未提供验收结论")), List.of("acceptanceResult", "acceptanceRemark")));
    return new HostedRectificationSummaryResponse(UUID.randomUUID().toString(), request.orderId(), request.orderVersion(), "DRAFT",
        digest(request), sections, safe(request.citations()),
        List.of(new HostedRectificationSummaryResponse.TraceStep("ASSEMBLE_FACTS", "读取关闭工单冻结事实", true),
            new HostedRectificationSummaryResponse.TraceStep("GENERATE_DRAFT", "生成整改说明总结草稿", true)));
  }

  private void requireContext(Object context) { if (context == null) throw new IllegalArgumentException("缺少可信 SafeGuard 执行上下文"); }
  private List<String> safe(List<String> values) { return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList(); }
  private boolean containsEvidence(List<String> evidence, String criterion) { String expected = criterion.toLowerCase(Locale.ROOT); return evidence.stream().anyMatch(item -> item.toLowerCase(Locale.ROOT).contains(expected) || expected.contains(item.toLowerCase(Locale.ROOT))); }
  private String join(List<String> values) { return String.join("；", safe(values)); }
  private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
  private String digest(Object value) { try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }

  private VisualEvidence analyzeImages(List<String> values, String phase, HostedRectificationReviewRequest request, boolean tolerateFailure) {
    if (values == null || values.isEmpty()) return new VisualEvidence(List.of(), true, false, false);
    if (values.size() > 8) throw new IllegalArgumentException("图片证据数量不能超过 8 张");
    List<String> result = new ArrayList<>();
    boolean confirmedRisk = false;
    boolean uncertainRisk = false;
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      if (value.length() > 8_000_000) throw new IllegalArgumentException("单张图片证据过大");
      int comma = value.indexOf(',');
      if (!value.startsWith("data:image/") || comma < 0) throw new IllegalArgumentException("仅支持 data:image Base64 图片证据");
      byte[] bytes = Base64.getDecoder().decode(value.substring(comma + 1));
      try {
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IllegalArgumentException("图片格式不可识别");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        result.add(phase + " image:" + hash + ":" + image.getWidth() + "x" + image.getHeight());
        VisualHazardContext vision = visualHazardService.analyze(new VisualHazardAnalysisRequest(
            "[RECTIFICATION_REVIEW]" + phase + "图片逐项整改复核。请把以下内容作为验收条件逐项核对：" + request.criteria().stream().map(HostedRectificationReviewRequest.Criterion::text).reduce((a, b) -> a + "；" + b).orElse("")
                + "。只报告图片明确显示不符合的项目；若现场可见措施已满足，不要生成隐患候选；不要把措施文字本身复述为风险。",
            value, request.executionContext()));
        for (VisualHazardContext.Candidate candidate : vision.hazardCandidates()) {
          result.add(phase + "视觉证据：" + candidate.description() + "；" + String.join("、", candidate.visibleEvidence()));
          if ("CONFIRMED".equals(candidate.judgement())) confirmedRisk = true;
          if ("SUSPECTED".equals(candidate.judgement()) || "UNKNOWN".equals(candidate.judgement())) uncertainRisk = true;
        }
      } catch (IllegalArgumentException exception) {
        if (!tolerateFailure) throw exception;
        return new VisualEvidence(List.copyOf(result), false, confirmedRisk, true);
      } catch (Exception exception) {
        if (!tolerateFailure) throw new IllegalArgumentException("视觉模型调用失败", exception);
        return new VisualEvidence(List.copyOf(result), false, confirmedRisk, true);
      }
    }
    return new VisualEvidence(List.copyOf(result), true, confirmedRisk, uncertainRisk);
  }

  private record VisualEvidence(List<String> evidence, boolean succeeded, boolean confirmedRisk, boolean uncertainRisk) {}
}
