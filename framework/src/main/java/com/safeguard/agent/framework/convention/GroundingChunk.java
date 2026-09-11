package com.safeguard.agent.framework.convention;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推荐问题 grounding 片段
 * <p>
 * 由检索片段按文档取最高分、截断文本后得到，随 assistant 消息落库，
 * 供推荐追问问题生成时 grounding：保证追问落在系统已掌握的证据面内（可答）、与已答内容发散（不集中）
 * <p>
 * 与 {@link SourceRef} 职责分离：SourceRef 面向来源面板/预览（摘录 100 字），
 * 本类面向推荐生成 grounding（片段文本更长）。两者均不参与模型回答上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroundingChunk {

    /**
     * 文档名称 供生成追问时识别证据所属文档
     */
    private String docName;

    /**
     * 片段全文 作为追问 grounding 的证据内容
     */
    private String text;
}
