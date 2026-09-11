package com.safeguard.agent.legal.filter;

import com.safeguard.agent.core.parser.model.Block;
import com.safeguard.agent.core.parser.model.HeadingBlock;
import com.safeguard.agent.core.parser.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Removes known non-body sections from MinerU output before legal parsing. */
@Component
public class LegalSectionFilter {

    private static final Pattern TOC_ENTRY = Pattern.compile(
            "(?i)^.+(?:[.·…_]{2,}|\\s{2,})\\s*[（(]?\\s*(?:\\d{1,4}|[ivxlcdmⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+)\\s*[）)]?$");
    private static final Pattern BODY_CHAPTER = Pattern.compile(
            "^(?:第[一二三四五六七八九十百零〇两]+章|附录\\s*[A-Za-z一二三四五六七八九十百零〇两]*)\\s*.*$");
    private static final Pattern NUMBERED_BODY_CHAPTER = Pattern.compile(
            "^\\d{1,2}\\s+[^.。！？；;:]{1,20}$");
    private static final Pattern COMPACT_BODY_CHAPTER = Pattern.compile(
            "^\\d{1,2}[\\p{IsHan}]{1,20}$");

    public FilterResult filter(String documentId, ParsedDocument parsed) {
        if (parsed == null || parsed.blocks() == null || parsed.blocks().isEmpty()) {
            return new FilterResult(ParsedDocument.of(List.of()), List.of());
        }
        List<Block> retained = new ArrayList<>();
        List<FilterLog> logs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int bodyStart = findBodyStart(parsed.blocks());
        int frontLimit = Math.min(80, Math.max(3, parsed.blocks().size() / 3));
        SectionState state = SectionState.BODY;
        boolean bodyStarted = bodyStart < 0;
        for (int i = 0; i < parsed.blocks().size(); i++) {
            Block block = parsed.blocks().get(i);
            String normalized = blockText(block).strip();
            if (i == bodyStart) {
                state = SectionState.BODY;
                bodyStarted = true;
            }
            String section = headingSection(block);
            if ("PREFACE".equals(section) && !(block instanceof HeadingBlock && i <= frontLimit && bodyStart > i)) {
                // Fail open: an uncertain boundary may retain noise, but must not delete the body.
                warnings.add("PDF_PREFACE_RETAINED block=" + i + " reason=untrusted_title_or_missing_body_boundary");
                if (state == SectionState.APPENDIX) {
                    logs.add(log(documentId, state.sectionType, block, "位于已识别的附录区域"));
                    continue;
                }
                state = SectionState.BODY;
                retained.add(block);
                continue;
            }
            if (state == SectionState.BODY && isBodyChapter(block, normalized)) bodyStarted = true;
            if (i < bodyStart && isTocState(state) && "APPENDIX".equals(section)) section = state.sectionType;
            if (section != null) {
                state = SectionState.from(section);
                logs.add(log(documentId, section, block, reason(section, normalized)));
                continue;
            }
            if (state != SectionState.BODY) {
                // A real chapter starts the body again; ordinary numbered clauses do not.
                boolean tocBoundary = isTocState(state) && block instanceof HeadingBlock && !isTocEntry(normalized);
                if (isTocState(state) && !bodyStarted && (isBodyChapter(block, normalized) || tocBoundary)) {
                    state = SectionState.BODY;
                    retained.add(block);
                } else {
                    logs.add(log(documentId, state.sectionType, block,
                            state == SectionState.PREFACE ? "前部可信标题至已验证正文起点" : "位于已识别的非正文区域"));
                }
                continue;
            }
            if (isTocEntry(normalized) && (bodyStart < 0 || i < bodyStart)) {
                logs.add(log(documentId, "CHINESE_TOC", block, "标题+页码连续结构"));
            } else {
                retained.add(block);
            }
        }
        Map<String, Object> metadata = new LinkedHashMap<>(parsed.metadata() == null ? Map.of() : parsed.metadata());
        metadata.put("legalSectionFilterLogs", List.copyOf(logs));
        metadata.put("legalSectionFilterRemovedBlocks", logs.size());
        metadata.put("legalSectionFilterWarnings", List.copyOf(warnings));
        if (bodyStart >= 0) {
            int retainedBody = java.util.stream.IntStream.range(0, retained.size())
                    .filter(i -> retained.get(i) == parsed.blocks().get(bodyStart)).findFirst().orElse(-1);
            if (retainedBody >= 0) metadata.put("legalBodyStartBlock", retainedBody);
        }
        return new FilterResult(ParsedDocument.of(retained, metadata), List.copyOf(logs));
    }

    private String headingSection(Block block) {
        String compact = compact(blockText(block));
        if (compact.equals("前言")) return "PREFACE";
        if (compact.equals("目录") || compact.equals("目次")) return "CHINESE_TOC";
        if (compact.equalsIgnoreCase("CONTENTS") || compact.equalsIgnoreCase("TABLEOFCONTENTS")) return "ENGLISH_TOC";
        if (compact.equals("引用标准名录")) return "REFERENCED_STANDARDS";
        if (compact.equals("本标准用词说明") || compact.equals("本规范用词说明")) return "TERMINOLOGY_NOTE";
        if (isTocEntry(blockText(block))) return null;
        boolean appendixTitle = block instanceof HeadingBlock || compact.matches("(?i)(?:附录[A-Z一二三四五六七八九十]|APPENDIX[A-Z]?)");
        if (appendixTitle && (compact.matches("附录[A-Za-z一二三四五六七八九十百零〇两].*")
                || compact.toUpperCase(Locale.ROOT).matches("APPENDIX(?:[A-Z].*)?"))) return "APPENDIX";
        return null;
    }

    private static boolean isTocState(SectionState state) {
        return state == SectionState.CHINESE_TOC || state == SectionState.ENGLISH_TOC;
    }

    private static String compact(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]+", "");
    }

    private int findBodyStart(List<Block> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            String value = compact(blockText(block));
            if (isTocEntry(blockText(block))) continue;
            if (value.matches("^第一章.+") || value.matches("^第一条.+")) return i;
            if (block instanceof HeadingBlock && value.matches("^(?:1(?:总则|范围|适用范围|一般规定|基本规定).*)$")) return i;
            // OCR may lose the '1 范围' heading. A scope statement following standalone
            // headings still proves body start; preserve those headings instead of guessing OCR text.
            if (block instanceof com.safeguard.agent.core.parser.model.ParagraphBlock
                    && value.matches("^本(?:标准|规范|规程)(?:规定了|适用于).*") && i > 0
                    && blocks.get(i - 1) instanceof HeadingBlock && headingSection(blocks.get(i - 1)) == null) {
                int first = i - 1;
                if (first > 0 && blocks.get(first - 1) instanceof HeadingBlock
                        && headingSection(blocks.get(first - 1)) == null) first--;
                return first;
            }
        }
        return -1;
    }

    private boolean isBodyChapter(Block block, String text) {
        boolean numberedHeading = block instanceof HeadingBlock
                && (NUMBERED_BODY_CHAPTER.matcher(text).matches() || COMPACT_BODY_CHAPTER.matcher(text).matches());
        return (BODY_CHAPTER.matcher(text).matches() || numberedHeading)
                && !isTocEntry(text) && headingSection(block) == null;
    }

    private boolean isTocEntry(String text) {
        String[] lines = text.strip().split("\\R");
        long entries = java.util.Arrays.stream(lines).filter(line -> TOC_ENTRY.matcher(line.strip()).matches()).count();
        return entries > 0 && (lines.length == 1 || entries >= 2 && entries * 2 >= lines.length);
    }

    private FilterLog log(String documentId, String section, Block block, String reason) {
        return new FilterLog(documentId, section, "unknown", reason);
    }

    private String reason(String section, String text) {
        return switch (section) {
            case "PREFACE" -> "关键词：前言";
            case "CHINESE_TOC" -> "关键词：目录/目 录";
            case "ENGLISH_TOC" -> "关键词：CONTENTS/Contents/TABLE OF CONTENTS";
            case "REFERENCED_STANDARDS" -> "关键词：引用标准名录";
            case "TERMINOLOGY_NOTE" -> "关键词：本标准/本规范用词说明";
            case "APPENDIX" -> "关键词：附录/Appendix";
            default -> text;
        };
    }

    private String blockText(Block block) {
        if (block instanceof HeadingBlock heading) return heading.text() == null ? "" : heading.text();
        if (block instanceof com.safeguard.agent.core.parser.model.ParagraphBlock paragraph) return paragraph.text() == null ? "" : paragraph.text();
        if (block instanceof com.safeguard.agent.core.parser.model.CodeBlock code) return code.code();
        if (block instanceof com.safeguard.agent.core.parser.model.ListBlock list) return String.join(" ", list.items());
        if (block instanceof com.safeguard.agent.core.parser.model.HtmlTableBlock table) return table.html();
        if (block instanceof com.safeguard.agent.core.parser.model.TableBlock table) return String.join(" ", table.headers());
        return "";
    }

    public record FilterResult(ParsedDocument document, List<FilterLog> logs) {
        public FilterResult {
            logs = logs == null ? List.of() : List.copyOf(logs);
        }
    }

    public record FilterLog(String document, String sectionType, String pageRange, String reason) {
    }

    private enum SectionState {
        BODY("BODY"), PREFACE("PREFACE"), CHINESE_TOC("CHINESE_TOC"), ENGLISH_TOC("ENGLISH_TOC"),
        REFERENCED_STANDARDS("REFERENCED_STANDARDS"), TERMINOLOGY_NOTE("TERMINOLOGY_NOTE"), APPENDIX("APPENDIX");

        private final String sectionType;
        SectionState(String sectionType) { this.sectionType = sectionType; }
        static SectionState from(String value) { return valueOf(value); }
    }
}
