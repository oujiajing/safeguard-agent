package com.safeguard.agent.legal.review;

import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.enums.LegalStructureType;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalSubUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnumerationSequenceGapDetectorTest {

    @Test
    void detectsGapFromStructuredChildren() {
        LegalClause clause = clause(List.of(
                new LegalSubUnit(LegalStructureType.ITEM, "1", "", "", 0),
                new LegalSubUnit(LegalStructureType.ITEM, "3", "", "", 1)));

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(clause))).singleElement()
                .satisfies(signal -> assertThat(signal.evidence()).containsEntry("missing", List.of("2")));
    }

    @Test
    void ignoresOrdinaryNumbersAndCompleteList() {
        LegalClause complete = clause(List.of(
                new LegalSubUnit(LegalStructureType.ITEM, "1", "", "", 0),
                new LegalSubUnit(LegalStructureType.ITEM, "2", "", "", 1)));
        LegalClause ordinary = new LegalClause("b", "doc", LegalContentRole.NORMATIVE, LegalStructureType.CLAUSE,
                "1", "", "", "", "1.1", "", "标准 2024 尺寸 3", "标准 2024 尺寸 3", List.of(), "e", "e", 1, 1, 0, 1);

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(complete, ordinary))).isEmpty();
    }

    @Test
    void recognizesMarkerJoinedDirectlyToChineseText() {
        LegalClause clause = clause(List.of(
                new LegalSubUnit(LegalStructureType.ITEM, "1", "", "", 0),
                new LegalSubUnit(LegalStructureType.ITEM, "2", "", "", 1),
                new LegalSubUnit(LegalStructureType.ITEM, "3", "", "", 2),
                new LegalSubUnit(LegalStructureType.ITEM, "5", "", "", 4)));
        LegalClause withRawText = new LegalClause(clause.clauseId(), clause.documentId(), clause.contentRole(), clause.structureType(),
                clause.chapterNo(), clause.chapterTitle(), clause.sectionNo(), clause.sectionTitle(), clause.clauseNo(), clause.hierarchyPath(),
                "1地下水范围\n2施工方案\n3勘察资料\n4周围建构筑物\n5现场条件", clause.normalizedText(), clause.children(),
                clause.firstElementId(), clause.lastElementId(), clause.pageStart(), clause.pageEnd(), clause.sourceStartOffset(), clause.sourceEndOffset());

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(withRawText))).isEmpty();
    }

    @Test
    void doesNotReportExpectedMarkerJoinedToLeadingContentNumber() {
        LegalClause clause = clause(List.of(
                new LegalSubUnit(LegalStructureType.ITEM, "1", "1 建筑高度大于33m的住宅建筑；", "", 0),
                new LegalSubUnit(LegalStructureType.PARAGRAPH, null,
                        "25层及以上且建筑面积大于3000m²（包括设置在其他建筑内第五层及以上楼层）的老年人照料设施；", "", 1),
                new LegalSubUnit(LegalStructureType.ITEM, "3", "3 一类高层公共建筑；", "", 2)));

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(clause))).isEmpty();
    }

    @Test
    void doesNotReportExpectedMarkerJoinedToRomanNumeralContent() {
        List<LegalSubUnit> children = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            children.add(new LegalSubUnit(LegalStructureType.ITEM, String.valueOf(i), i + " 第" + i + "项；", "", i - 1));
        }
        children.add(new LegalSubUnit(LegalStructureType.PARAGRAPH, null, "8Ⅱ类、Ⅲ类汽车库和I类修车库；", "", 7));
        children.add(new LegalSubUnit(LegalStructureType.ITEM, "9", "9 其他公共建筑；", "", 8));

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(clause(children)))).isEmpty();
    }

    @Test
    void doesNotMergeNestedRestartedListWithOuterList() {
        List<LegalSubUnit> outerItems = List.of(
                new LegalSubUnit(LegalStructureType.ITEM, "1", "", "", 0),
                new LegalSubUnit(LegalStructureType.ITEM, "2", "", "", 1),
                new LegalSubUnit(LegalStructureType.ITEM, "3", "", "", 2),
                new LegalSubUnit(LegalStructureType.ITEM, "4", "", "", 3),
                new LegalSubUnit(LegalStructureType.ITEM, "5", "", "", 4),
                new LegalSubUnit(LegalStructureType.ITEM, "6", "", "", 5),
                new LegalSubUnit(LegalStructureType.ITEM, "7", "", "", 9),
                new LegalSubUnit(LegalStructureType.ITEM, "8", "", "", 10));
        LegalClause clause = new LegalClause("nested", "doc", LegalContentRole.NORMATIVE, LegalStructureType.CLAUSE,
                "1", "", "", "", "1.1", "", "1\n2\n3\n4\n5\n6\n1）nested\n2）nested\n3）nested\n7\n8", "", outerItems, "e", "e", 1, 1, 0, 1);

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(clause))).isEmpty();
    }

    @Test
    void keepsDirectlyJoinedOuterItemsAndSkipsNestedItemsAfter下列() {
        List<LegalSubUnit> children = List.of(
                new LegalSubUnit(LegalStructureType.ITEM, "1", "1 第一项", "", 0),
                new LegalSubUnit(LegalStructureType.PARAGRAPH, null, "2第二项", "", 1),
                new LegalSubUnit(LegalStructureType.ITEM, "3", "3 第三项", "", 2),
                new LegalSubUnit(LegalStructureType.PARAGRAPH, null, "4第四项", "", 3),
                new LegalSubUnit(LegalStructureType.ITEM, "5", "5 第五项", "", 4),
                new LegalSubUnit(LegalStructureType.ITEM, "6", "6 还应符合下列安全装置", "", 5),
                new LegalSubUnit(LegalStructureType.PARAGRAPH, null, "1）内部第一项", "", 6),
                new LegalSubUnit(LegalStructureType.PARAGRAPH, null, "2）内部第二项", "", 7),
                new LegalSubUnit(LegalStructureType.ITEM, "7", "7 第七项", "", 8));
        LegalClause clause = new LegalClause("nested-joined", "doc", LegalContentRole.NORMATIVE, LegalStructureType.CLAUSE,
                "1", "", "", "", "1.1", "", "", "", children, "e", "e", 1, 1, 0, 1);

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(clause))).isEmpty();
    }

    private static LegalClause clause(List<LegalSubUnit> children) {
        return new LegalClause("a", "doc", LegalContentRole.NORMATIVE, LegalStructureType.CLAUSE,
                "1", "", "", "", "1.1", "", "", "", children, "e", "e", 1, 1, 0, 1);
    }
}
