package com.safeguard.agent.legal.diagnostics;

import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.ingest.CleanedTextImportMode;
import com.safeguard.agent.legal.ingest.CleanedTextImporter;
import com.safeguard.agent.legal.model.CleanedTextImportResult;
import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalDocumentElement;
import com.safeguard.agent.legal.model.NormalizedLegalDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class LegalCorpusDiagnosticsService {

    private static final Pattern HEADING = Pattern.compile("^(?:第[一二三四五六七八九十百零〇两]+章|第[一二三四五六七八九十百零〇两]+节|\\d{1,2}(?:\\.\\d+)?\\s+[^。！？；;]{1,30})$");
    private static final Pattern TABLE = Pattern.compile("(?:\\|.+\\||\\t.+\\t|表\\s*[A-Za-z一二三四五六七八九十0-9-]*)");
    private static final Pattern FIGURE = Pattern.compile("^(?:图|图示|图\\s*\\d|Fig\\.?\\s*\\d).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMULA = Pattern.compile(".*(?:[=≤≥Σφλμ]|式\\s*\\(?\\d).*");
    private static final Pattern METADATA = Pattern.compile("^(?:中华人民共和国|批准部门|发布部门|主编单位|参编单位|施行日期|发布日期|公告|第\\s*\\d+\\s*号).*");

    private final CleanedTextImporter importer;

    public LegalCorpusDiagnosticsService(CleanedTextImporter importer) {
        this.importer = importer;
    }

    public LegalDocumentDiagnostics analyze(Path source) throws IOException {
        String documentId = "corpus-" + Integer.toUnsignedString(source.getFileName().toString().hashCode(), 36);
        byte[] bytes = Files.readAllBytes(source);
        CleanedTextImportResult first = importer.importText(documentId, source.getFileName().toString(), bytes,
                CleanedTextImportMode.DRY_RUN);
        CleanedTextImportResult second = importer.importText(documentId, source.getFileName().toString(), bytes,
                CleanedTextImportMode.DRY_RUN);
        boolean deterministic = signature(first).equals(signature(second));
        NormalizedLegalDocument document = first.document();
        Coverage coverage = coverage(document, first.canonicalSourceText());
        return new LegalDocumentDiagnostics(source.getFileName().toString(), first,
                duplicateGroups(source, document), unstructured(source, document), metadataWarnings(document),
                ratio(coverage.accountedLength(), coverage.sourceLength()), coverage.sourceLength(),
                coverage.accountedLength(), coverage.sourceLength() - coverage.accountedLength(), coverage.fragments(),
                ratio(coverage.structuredLength(), coverage.sourceLength()), deterministic);
    }

    public List<LegalDocumentDiagnostics> analyzeAll(Path corpus) throws IOException {
        try (var stream = Files.list(corpus)) {
            List<Path> files = stream.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            List<LegalDocumentDiagnostics> result = new ArrayList<>();
            for (Path file : files) result.add(analyze(file));
            return List.copyOf(result);
        }
    }

    public static String signature(CleanedTextImportResult result) {
        StringBuilder out = new StringBuilder();
        out.append(result.document().clauses().size()).append('|').append(result.chunks().size()).append('|');
        result.document().clauses().forEach(clause -> out.append(clause.clauseId()).append(':')
                .append(clause.contentRole()).append(':').append(clause.clauseNo()).append(':')
                .append(clause.hierarchyPath()).append(':').append(sha256(clause.rawText())).append(':')
                .append(sha256(clause.normalizedText())).append(';'));
        result.chunks().forEach(chunk -> out.append(chunk.chunkIndex()).append(':')
                .append(chunk.metadata().parentClauseId()).append(':').append(chunk.metadata().clauseNo()).append(':')
                .append(sha256(chunk.content())).append(':').append(sha256(chunk.metadata().toMap().toString())).append(';'));
        out.append(result.qualityReport().qualityStatus()).append(':').append(result.qualityReport().warnings());
        return sha256(out.toString());
    }

    private List<LegalDuplicateGroup> duplicateGroups(Path source, NormalizedLegalDocument document) {
        Map<String, List<LegalClause>> byRoleNumber = new LinkedHashMap<>();
        for (LegalClause clause : document.clauses()) {
            byRoleNumber.computeIfAbsent(clause.contentRole() + "|" + clause.clauseNo(), ignored -> new ArrayList<>()).add(clause);
        }
        List<LegalDuplicateGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<LegalClause>> entry : byRoleNumber.entrySet()) {
            List<LegalClause> clauses = entry.getValue();
            if (clauses.size() < 2) continue;
            Map<String, List<LegalClause>> byText = new LinkedHashMap<>();
            clauses.forEach(clause -> byText.computeIfAbsent(normalizeForCompare(clause.normalizedText()), ignored -> new ArrayList<>()).add(clause));
            for (List<LegalClause> sameText : byText.values()) {
                if (sameText.size() > 1) {
                    groups.add(group(source, sameText, LegalDuplicateType.EXACT_DUPLICATE,
                            originFor(sameText, LegalDuplicateType.SOURCE_EXACT_DUPLICATE)));
                }
            }
            if (byText.size() > 1) {
                groups.add(group(source, clauses, LegalDuplicateType.NEAR_DUPLICATE,
                        originFor(clauses, LegalDuplicateType.SOURCE_NEAR_DUPLICATE)));
            }
        }
        Map<String, Set<LegalContentRole>> roles = new HashMap<>();
        Map<String, List<LegalClause>> roleClauses = new HashMap<>();
        for (LegalClause clause : document.clauses()) {
            roles.computeIfAbsent(clause.clauseNo(), ignored -> new HashSet<>()).add(clause.contentRole());
            roleClauses.computeIfAbsent(clause.clauseNo(), ignored -> new ArrayList<>()).add(clause);
        }
        for (Map.Entry<String, Set<LegalContentRole>> entry : roles.entrySet()) {
            if (entry.getValue().contains(LegalContentRole.NORMATIVE)
                    && entry.getValue().contains(LegalContentRole.COMMENTARY)) {
                groups.add(group(source, roleClauses.get(entry.getKey()), LegalDuplicateType.SAME_NUMBER_DIFFERENT_ROLE,
                        LegalDuplicateType.STRUCTURAL_VALID));
            }
        }
        return List.copyOf(groups);
    }

    private LegalDuplicateGroup group(Path source,
                                      List<LegalClause> clauses,
                                      LegalDuplicateType type,
                                      LegalDuplicateType origin) {
        LegalClause first = clauses.get(0);
        return new LegalDuplicateGroup(source.getFileName().toString(), first.contentRole(), first.clauseNo(), type, origin,
                clauses.size(), clauses.stream().limit(2).map(c -> preview(c.normalizedText(), 240)).toList());
    }

    private boolean rawTextsIdentical(List<LegalClause> clauses) {
        return clauses.stream().map(LegalClause::rawText).distinct().count() == 1;
    }

    private LegalDuplicateType originFor(List<LegalClause> clauses, LegalDuplicateType sourceOrigin) {
        boolean sameSpan = clauses.stream().map(c -> c.sourceStartOffset() + ":" + c.sourceEndOffset()).distinct().count() == 1;
        if (sameSpan) return LegalDuplicateType.PARSER_DUPLICATE;
        return sourceOrigin;
    }

    private List<LegalUnstructuredItem> unstructured(Path source, NormalizedLegalDocument document) {
        return document.unstructuredParagraphs().stream()
                .map(element -> new LegalUnstructuredItem(source.getFileName().toString(), element.elementIndex(),
                        element.rawText(), classify(element.rawText())))
                .toList();
    }

    private UnstructuredDiagnosticType classify(String text) {
        if (text == null || text.isBlank()) return UnstructuredDiagnosticType.UNKNOWN;
        if (HEADING.matcher(text.strip()).matches()) return UnstructuredDiagnosticType.HEADING_LIKE;
        if (TABLE.matcher(text).find()) return UnstructuredDiagnosticType.TABLE_RESIDUE;
        if (FIGURE.matcher(text.strip()).matches()) return UnstructuredDiagnosticType.FIGURE_CAPTION;
        if (FORMULA.matcher(text).matches()) return UnstructuredDiagnosticType.FORMULA_LIKE;
        if (METADATA.matcher(text.strip()).matches()) return UnstructuredDiagnosticType.METADATA;
        if (text.contains("附录")) return UnstructuredDiagnosticType.APPENDIX_TEXT;
        return UnstructuredDiagnosticType.NORMAL_PARAGRAPH_UNKNOWN;
    }

    private List<String> metadataWarnings(NormalizedLegalDocument document) {
        List<String> warnings = new ArrayList<>(document.warnings());
        var metadata = document.metadata();
        if (metadata.docTitle() == null || metadata.docTitle().isBlank()) warnings.add("title missing");
        if (metadata.standardNo() == null || metadata.standardNo().isBlank()) warnings.add("standardNo missing");
        if (!metadata.sourceFile().matches(".*(?:19|20)\\d{2}.*")) warnings.add("year missing");
        return List.copyOf(warnings);
    }

    private Coverage coverage(NormalizedLegalDocument document, String canonicalSource) {
        int total = canonicalSource == null ? 0 : (int) canonicalSource.chars().filter(ch -> !Character.isWhitespace(ch)).count();
        if (total == 0) return new Coverage(0, 0, List.of(), 0);
        Set<Integer> covered = new HashSet<>();
        document.elements().forEach(e -> {
            if (e.structureType() != com.safeguard.agent.legal.enums.LegalStructureType.UNKNOWN) covered.add(e.elementIndex());
        });
        document.unstructuredParagraphs().forEach(e -> covered.add(e.elementIndex()));
        int[] owner = new int[canonicalSource.length()];
        java.util.Arrays.fill(owner, 0);
        for (LegalDocumentElement element : document.elements()) {
            if (!covered.contains(element.elementIndex())) continue;
            int start = Math.min(element.sourceStartOffset(), canonicalSource.length());
            int end = Math.min(element.sourceEndOffset(), canonicalSource.length());
            for (int i = start; i < end; i++) owner[i] = 1;
        }
        int accounted = 0;
        for (int i = 0; i < owner.length; i++) {
            if (owner[i] == 1 && !Character.isWhitespace(canonicalSource.charAt(i))) accounted++;
        }
        List<String> fragments = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= owner.length; i++) {
            boolean missing = i < owner.length && owner[i] == 0 && !Character.isWhitespace(canonicalSource.charAt(i));
            if (missing && start < 0) start = i;
            if (!missing && start >= 0) {
                if (fragments.size() < 20) fragments.add(canonicalSource.substring(start, i));
                start = -1;
            }
        }
        Set<Integer> structured = new HashSet<>();
        document.clauses().forEach(clause -> clause.children().forEach(child -> structured.add(child.elementIndex())));
        int structuredLength = document.elements().stream().filter(e -> structured.contains(e.elementIndex()))
                .mapToInt(e -> (int) e.normalizedText().chars().filter(ch -> !Character.isWhitespace(ch)).count()).sum();
        return new Coverage(total, accounted, fragments, structuredLength);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0D : (double) numerator / denominator;
    }

    private record Coverage(int sourceLength, int accountedLength, List<String> fragments, int structuredLength) {
    }

    private String normalizeForCompare(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "").replace("。", ".");
    }

    private String preview(String text, int length) {
        String value = text == null ? "" : text.replace('\n', ' ').strip();
        return value.length() <= length ? value : value.substring(0, length) + "...";
    }
}
