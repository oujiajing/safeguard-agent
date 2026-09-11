package com.safeguard.agent.legal.ingest;

import com.safeguard.agent.legal.chunk.LegalChunker;
import com.safeguard.agent.legal.clean.LegalCleaningPipeline;
import com.safeguard.agent.legal.enums.LegalSourceFormat;
import com.safeguard.agent.legal.metadata.LegalMetadataExtractor;
import com.safeguard.agent.legal.metadata.MetadataExtractionResult;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.model.LegalChunk;
import com.safeguard.agent.legal.model.LegalDocumentElement;
import com.safeguard.agent.legal.model.LegalSourceBlock;
import com.safeguard.agent.legal.model.NormalizedLegalDocument;
import com.safeguard.agent.legal.parser.LegalStructureParser;
import com.safeguard.agent.legal.qc.LegalQualityService;
import com.safeguard.agent.legal.util.LegalHashes;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CleanedTextImporter {

    public static final String PARSER_VERSION = "legal-txt-parser/1.0.0";

    private final LegalCleaningPipeline cleaningPipeline;
    private final LegalMetadataExtractor metadataExtractor;
    private final LegalStructureParser structureParser;
    private final LegalChunker chunker;
    private final LegalQualityService qualityService;

    public CleanedTextImporter(LegalCleaningPipeline cleaningPipeline,
                               LegalMetadataExtractor metadataExtractor,
                               LegalStructureParser structureParser,
                               LegalChunker chunker,
                               LegalQualityService qualityService) {
        this.cleaningPipeline = cleaningPipeline;
        this.metadataExtractor = metadataExtractor;
        this.structureParser = structureParser;
        this.chunker = chunker;
        this.qualityService = qualityService;
    }

    public CleanedTextImportResult importText(String documentId,
                                              String sourceFile,
                                              byte[] rawBytes,
                                              CleanedTextImportMode mode) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new IllegalArgumentException("TXT bytes 不能为空");
        }
        return importCanonicalText(documentId, sourceFile, decodeUtf8(rawBytes), rawBytes, mode,
                LegalSourceFormat.CLEANED_TXT, PARSER_VERSION);
    }

    /**
     * 把已经由通用 Document Parser 产出的文本送入同一套法规清洗、结构化、分块和质检链路。
     * originalBytes 用于幂等 hash，canonicalText 只作为法规解析输入，避免用 MinerU Markdown
     * 覆盖原始 PDF 身份。
     */
    public CleanedTextImportResult importCanonicalText(String documentId,
                                                       String sourceFile,
                                                       String canonicalText,
                                                       byte[] originalBytes,
                                                       CleanedTextImportMode mode,
                                                       LegalSourceFormat sourceFormat,
                                                       String parserVersion) {
        if (mode == null) throw new IllegalArgumentException("导入模式不能为空");
        if (originalBytes == null || originalBytes.length == 0) {
            throw new IllegalArgumentException("原始文档 bytes 不能为空");
        }
        if (canonicalText == null || canonicalText.isBlank()) {
            throw new IllegalArgumentException("法规解析文本不能为空");
        }
        if (sourceFormat == null || parserVersion == null || parserVersion.isBlank()) {
            throw new IllegalArgumentException("法规来源格式和解析器版本不能为空");
        }
        List<LegalDocumentElement> elements = cleaningPipeline.clean(documentId, canonicalText);
        return importElements(documentId, sourceFile, originalBytes, sourceFormat, parserVersion, elements, List.of());
    }

    public CleanedTextImportResult importPdfBlocks(String documentId, String sourceFile, byte[] originalBytes,
                                                   CleanedTextImportMode mode, String parserVersion,
                                                   List<LegalSourceBlock> blocks, List<String> warnings) {
        if (mode == null || originalBytes == null || originalBytes.length == 0 || blocks == null || blocks.isEmpty()
                || parserVersion == null || parserVersion.isBlank()) throw new IllegalArgumentException("PDF 导入参数不能为空");
        return importElements(documentId, sourceFile, originalBytes, LegalSourceFormat.MINERU_PDF, parserVersion,
                cleaningPipeline.cleanBlocks(documentId, blocks), warnings);
    }

    private CleanedTextImportResult importElements(String documentId, String sourceFile, byte[] originalBytes,
                                                   LegalSourceFormat sourceFormat, String parserVersion,
                                                   List<LegalDocumentElement> elements, List<String> warnings) {
        String fileHash = LegalHashes.sha256(originalBytes);
        String normalizedText = String.join("\n", elements.stream().map(LegalDocumentElement::normalizedText).toList());
        MetadataExtractionResult extracted = metadataExtractor.extract(
                documentId, sourceFile, originalBytes, normalizedText, parserVersion, fileHash, sourceFormat);
        java.util.ArrayList<String> allWarnings = new java.util.ArrayList<>(extracted.warnings());
        if (warnings != null) allWarnings.addAll(warnings);
        NormalizedLegalDocument normalized = structureParser.parse(extracted.metadata(), elements, allWarnings);
        List<LegalChunk> chunks = chunker.chunk(normalized);
        return new CleanedTextImportResult(normalized, chunks, qualityService.assess(normalized, chunks), normalizedText);
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("cleaned TXT 不是有效 UTF-8", e);
        }
    }
}
