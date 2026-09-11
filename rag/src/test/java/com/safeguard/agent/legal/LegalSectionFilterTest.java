package com.safeguard.agent.legal;

import com.safeguard.agent.core.parser.model.Block;
import com.safeguard.agent.core.parser.model.HeadingBlock;
import com.safeguard.agent.core.parser.model.ParagraphBlock;
import com.safeguard.agent.core.parser.model.ParsedDocument;
import com.safeguard.agent.core.parser.model.Provenance;
import com.safeguard.agent.legal.filter.LegalSectionFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalSectionFilterTest {

    private static final Provenance PROVENANCE = Provenance.ofFile("fixture.pdf");

    @Test
    void removesAllConfiguredNonBodySectionsAndKeepsBodyChapterAndClauses() {
        List<Block> blocks = List.of(
                heading("前言"), paragraph("本前言不应入库"),
                heading("目 录"), paragraph("1 总则 ........ 1"), paragraph("2 术语 ........ 2"),
                heading("第一章 总则"), paragraph("1.1 正文条款"),
                heading("CONTENTS"), paragraph("3 Scope ........ 3"),
                heading("引用标准名录"), paragraph("GB/T 1234"),
                heading("本规范用词说明"), paragraph("本说明不应入库"),
                heading("附录A"), paragraph("附录内容不应入库"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-1", ParsedDocument.of(blocks));

        List<String> retained = result.document().blocks().stream()
                .map(block -> block instanceof HeadingBlock h ? h.text() : ((ParagraphBlock) block).text())
                .toList();
        assertEquals(List.of("第一章 总则", "1.1 正文条款"), retained);
        assertEquals(6, result.logs().stream().map(LegalSectionFilter.FilterLog::sectionType).distinct().count());
        assertTrue(result.logs().stream().allMatch(log -> log.document().equals("doc-1")));
        assertTrue(result.logs().stream().allMatch(log -> !log.reason().isBlank()));
        assertTrue(result.document().metadata().containsKey("legalSectionFilterLogs"));
    }

    @Test
    void doesNotTreatOrdinaryBodyNumberAsTocEntry() {
        List<Block> blocks = List.of(
                heading("第一章 总则"), paragraph("1 总则"), paragraph("1.1 施工单位应当建立安全制度"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-2", ParsedDocument.of(blocks));

        assertEquals(3, result.document().blocks().size());
        assertTrue(result.logs().isEmpty());
    }

    @Test
    void resumesBodyAtNumberedChapterAfterChineseToc() {
        List<Block> blocks = List.of(
                heading("目录"), paragraph("1 总则 ........ 1"),
                heading("1 总则"), paragraph("1.1 正文条款"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-3", ParsedDocument.of(blocks));

        assertEquals(List.of("1 总则", "1.1 正文条款"), result.document().blocks().stream()
                .map(block -> block instanceof HeadingBlock h ? h.text() : ((ParagraphBlock) block).text())
                .toList());
    }

    @Test
    void resumesBodyAtCompactNumberedHeadingAfterPreface() {
        List<Block> blocks = List.of(
                heading("前 言"), paragraph("前言内容"),
                heading("1范围"), paragraph("1.1 正文条款"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-4", ParsedDocument.of(blocks));

        assertEquals(List.of("1范围", "1.1 正文条款"), result.document().blocks().stream()
                .map(block -> block instanceof HeadingBlock h ? h.text() : ((ParagraphBlock) block).text())
                .toList());
    }

    @Test
    void doesNotResumeBodyAtSecondTocAfterBodyHasStarted() {
        List<Block> blocks = List.of(
                heading("1 总则"), paragraph("1.0.1 正文条款"),
                heading("目次"), paragraph("1 总则 ........ 1"), heading("1 总则"),
                paragraph("1.0.1 目录后的内容不应恢复为正文"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-5", ParsedDocument.of(blocks));

        assertEquals(List.of("1 总则", "1.0.1 正文条款"), result.document().blocks().stream()
                .map(block -> block instanceof HeadingBlock h ? h.text() : ((ParagraphBlock) block).text())
                .toList());
    }

    private Block heading(String text) {
        return new HeadingBlock(PROVENANCE, 1, text);
    }

    private Block paragraph(String text) {
        return new ParagraphBlock(PROVENANCE, text);
    }
}
