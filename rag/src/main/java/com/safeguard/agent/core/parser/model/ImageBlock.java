package com.safeguard.agent.core.parser.model;

/**
 * 图片 Block：由 ImageChunker 渲染成 {@code ![caption](url)} 的 atomic chunk，图片链接被切碎会导致前端渲染失败
 *
 * @param description VLM 图生文结果，同时用于 embedding 检索与喂 LLM 答题，MinerU 等不产图生文的来源为 null
 */
public record ImageBlock(
        Provenance provenance,
        AssetRef asset,
        String caption,
        String altText,
        String description
) implements Block {

    /**
     * 不产图生文的来源（MinerU / Excel 等）用此形态，description 置空
     */
    public ImageBlock(Provenance provenance, AssetRef asset, String caption, String altText) {
        this(provenance, asset, caption, altText, null);
    }
}
