package com.safeguard.agent.core.parser.model;

/**
 * 结构化解析产物的统一基类：解析器输出到 ChunkerNode 之间的内存中间表示，仅存活于解析阶段
 * <p>
 * sealed 保证编译期穷举，新增 Block 类型时所有 switch 必须显式处理；Block 只描述内容本身，章节路径由
 * HeadingHandler 在遍历时累积进 ChunkContext，入库的 markdown 展示文本由各 chunker 在切分阶段渲染；
 * 不带 Jackson 多态注解，全程无序列化出入口
 */
public sealed interface Block
        permits HeadingBlock, ParagraphBlock, TableBlock, HtmlTableBlock, ImageBlock, CodeBlock, ListBlock {

    /**
     * 来源信息：文件、页码 / sheet、bbox / 单元格范围
     */
    Provenance provenance();
}
