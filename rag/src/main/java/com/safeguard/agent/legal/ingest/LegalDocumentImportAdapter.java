package com.safeguard.agent.legal.ingest;

import com.safeguard.agent.core.parser.model.Block;
import com.safeguard.agent.core.parser.model.CodeBlock;
import com.safeguard.agent.core.parser.model.HeadingBlock;
import com.safeguard.agent.core.parser.model.HtmlTableBlock;
import com.safeguard.agent.core.parser.model.ImageBlock;
import com.safeguard.agent.core.parser.model.ListBlock;
import com.safeguard.agent.core.parser.model.ParagraphBlock;
import com.safeguard.agent.core.parser.model.ParsedDocument;
import com.safeguard.agent.core.parser.model.TableBlock;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.model.LegalSourceBlock;
import org.springframework.stereotype.Service;

import java.util.List;

/** Bridges the generic MinerU Document result into the existing legal text pipeline. */
@Service
public class LegalDocumentImportAdapter {

    public static final String PARSER_VERSION = "legal-pdf-mineru-adapter/2.0.0";

    private final CleanedTextImporter importer;

    public LegalDocumentImportAdapter(CleanedTextImporter importer) {
        this.importer = importer;
    }

    public CleanedTextImportResult importPdf(String documentId,
                                             String sourceFile,
                                             byte[] originalPdf,
                                             ParsedDocument parsed,
                                             CleanedTextImportMode mode) {
        if (parsed == null || parsed.blocks() == null || parsed.blocks().isEmpty()) {
            throw new IllegalArgumentException("MinerU ParsedDocument 为空");
        }
        java.util.ArrayList<LegalSourceBlock> source = new java.util.ArrayList<>();
        Object bodyIndex = parsed.metadata() == null ? null : parsed.metadata().get("legalBodyStartBlock");
        int bodyStart = bodyIndex instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
        for (int i = 0; i < parsed.blocks().size(); i++) {
            Block block = parsed.blocks().get(i);
            LegalSourceBlock.Kind kind = block instanceof HeadingBlock ? LegalSourceBlock.Kind.HEADING
                    : block instanceof HtmlTableBlock || block instanceof TableBlock ? LegalSourceBlock.Kind.TABLE
                    : block instanceof ListBlock ? LegalSourceBlock.Kind.LIST
                    : block instanceof ImageBlock ? LegalSourceBlock.Kind.IMAGE
                    : block instanceof CodeBlock ? LegalSourceBlock.Kind.CODE : LegalSourceBlock.Kind.PARAGRAPH;
            source.add(new LegalSourceBlock(renderForLegalParser(List.of(block)), kind, i >= bodyStart));
        }
        Object filterWarnings = parsed.metadata() == null ? null : parsed.metadata().get("legalSectionFilterWarnings");
        List<String> warnings = filterWarnings instanceof List<?> values ? values.stream().map(Object::toString).toList() : List.of();
        return importer.importPdfBlocks(documentId, sourceFile, originalPdf, mode, PARSER_VERSION, source, warnings);
    }

    static String renderForLegalParser(List<Block> blocks) {
        StringBuilder out = new StringBuilder();
        for (Block block : blocks) {
            if (block instanceof HeadingBlock heading) append(out, heading.text());
            else if (block instanceof ParagraphBlock paragraph) append(out, paragraph.text());
            else if (block instanceof HtmlTableBlock table) append(out, table.html());
            else if (block instanceof TableBlock table) {
                append(out, table.headers() == null ? "" : String.join(" | ", table.headers()));
                if (table.rows() != null) table.rows().forEach(row -> append(out, String.join(" | ", row)));
            } else if (block instanceof ListBlock list && list.items() != null) {
                list.items().forEach(item -> append(out, item));
            } else if (block instanceof CodeBlock code) append(out, code.code());
            else if (block instanceof ImageBlock image) {
                append(out, image.description());
                append(out, image.caption());
            }
        }
        return out.toString().strip();
    }

    private static void append(StringBuilder out, String value) {
        if (value != null && !value.isBlank()) out.append(value.strip()).append('\n');
    }
}
