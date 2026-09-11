package com.safeguard.agent.infra.vlm;

/**
 * 视觉大模型（VLM）访问接口
 * <p>
 * 与 LLMService / EmbeddingService / RerankService 同级的第四类模型能力
 * 当前唯一用途是知识库入库期的「图生文」：把图片转成可检索的中文描述 + 图中文字 OCR
 * 下游问答仍为纯文本模型，VLM 只在写入侧调用，不进入 chat 热路径
 */
public interface VlmService {

    /**
     * 图生文：输入图片字节，返回模型生成的文本（中文描述 + 图中文字）
     * <p>
     * 失败直接抛 {@link com.safeguard.agent.infra.http.ModelClientException}，不做兜底降级
     *
     * @param imageBytes      图片二进制
     * @param mime            图片 MIME，如 image/png、image/jpeg
     * @param prompt          引导提示词
     * @param maxOutputTokens 输出 token 上限，可空（控成本）
     * @return 模型返回的描述文本
     */
    String describeImage(byte[] imageBytes, String mime, String prompt, Integer maxOutputTokens);
}
