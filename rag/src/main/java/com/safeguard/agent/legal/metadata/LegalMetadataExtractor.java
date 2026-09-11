package com.safeguard.agent.legal.metadata;

import com.safeguard.agent.legal.enums.LegalSourceFormat;
import com.safeguard.agent.legal.model.LegalDocumentMetadata;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LegalMetadataExtractor {

    private static final Pattern BOOK_TITLE = Pattern.compile("《([^》]+)》");
    private static final Pattern STANDARD = Pattern.compile(
            "(?i)(GB\\s*(?:[/]\\s*T|T)?|JGJ\\s*(?:[/]\\s*T|T)?|CJJ\\s*(?:[/]\\s*T|T)?|CECS|GA)\\s*([0-9]{1,8})(?:\\s*[-—]\\s*((?:19|20)\\d{2}))?");
    private static final Pattern AUTHORITY = Pattern.compile("(?:批准部门|发布部门|制定机关)\\s*[:：]\\s*(.+)");
    private static final Pattern DATE = Pattern.compile("((?:19|20)\\d{2})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern EFFECTIVE_DATE = Pattern.compile("自\\s*((?:19|20)\\d{2})年(\\d{1,2})月(\\d{1,2})日(?:起)?施行");

    public MetadataExtractionResult extract(String documentId,
                                            String sourceFile,
                                            byte[] bytes,
                                            String normalizedText,
                                            String parserVersion,
                                            String fileHash) {
        return extract(documentId, sourceFile, bytes, normalizedText, parserVersion, fileHash,
                LegalSourceFormat.CLEANED_TXT);
    }

    public MetadataExtractionResult extract(String documentId,
                                            String sourceFile,
                                            byte[] bytes,
                                            String normalizedText,
                                            String parserVersion,
                                            String fileHash,
                                            LegalSourceFormat sourceFormat) {
        List<String> warnings = new ArrayList<>();
        String baseName = sourceFile.replaceFirst("(?i)\\.(txt|pdf)$", "");
        String header = firstLines(normalizedText, 60);

        String fileStandard = findStandard(baseName);
        String bodyStandard = findStandard(header);
        String standardNo = fileStandard != null ? fileStandard : bodyStandard;
        if (fileStandard != null && bodyStandard != null && !fileStandard.equals(bodyStandard)) {
            warnings.add("standardNo 冲突: filename=" + fileStandard + ", body=" + bodyStandard);
        }

        String title = findTitle(baseName);
        if (title == null) title = deriveTitle(baseName, fileStandard);
        if (title == null || title.isBlank()) title = findTitle(header);

        String docType = detectDocType(normalizedText);
        String authority = findGroup(AUTHORITY, header, 1);
        LocalDate effectiveDate = findDate(EFFECTIVE_DATE, header);
        LocalDate publishDate = firstDate(header);

        LegalDocumentMetadata metadata = new LegalDocumentMetadata(
                documentId,
                blankToNull(title),
                docType,
                standardNo,
                blankToNull(authority),
                publishDate,
                effectiveDate,
                sourceFile,
                sourceFormat,
                fileHash,
                parserVersion);
        return new MetadataExtractionResult(metadata, warnings);
    }

    private String findTitle(String text) {
        Matcher matcher = BOOK_TITLE.matcher(text);
        if (!matcher.find()) return null;
        return matcher.group(1)
                .replace("[附条文说明]", "")
                .replace("【附条文说明】", "")
                .strip();
    }

    private String deriveTitle(String baseName, String standardNo) {
        String title = baseName;
        if (standardNo != null) {
            Matcher matcher = STANDARD.matcher(baseName.replace('／', '/'));
            if (matcher.find()) title = baseName.substring(0, matcher.start());
        }
        return title.replace("[附条文说明]", "")
                .replace("【附条文说明】", "")
                .replaceAll("[《》]", "")
                .strip();
    }

    private String findStandard(String text) {
        if (text == null) return null;
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                .replace('／', '/').replace('—', '-');
        Matcher matcher = STANDARD.matcher(normalized);
        if (!matcher.find()) return null;
        String prefix = matcher.group(1).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        prefix = switch (prefix) {
            case "GBT", "GB/T" -> "GB/T";
            case "JGJT", "JGJ/T" -> "JGJ/T";
            case "CJJT", "CJJ/T" -> "CJJ/T";
            default -> prefix;
        };
        String number = matcher.group(2);
        String year = matcher.group(3);
        if (year == null && number.length() >= 6) {
            String possibleYear = number.substring(number.length() - 4);
            int y = Integer.parseInt(possibleYear);
            if (y >= 1900 && y <= 2099) {
                year = possibleYear;
                number = number.substring(0, number.length() - 4);
            }
        }
        return prefix + " " + number + (year == null ? "" : "-" + year);
    }

    private String detectDocType(String text) {
        if (text != null && Pattern.compile("(?m)^\\s*第[一二三四五六七八九十百零〇两]+条").matcher(text).find()) {
            return "LAW_REGULATION";
        }
        if (text != null && Pattern.compile("(?m)^\\s*\\d+(?:\\s*\\.\\s*\\d+){1,4}\\s*").matcher(text).find()) {
            return "ENGINEERING_STANDARD";
        }
        return null;
    }

    private LocalDate firstDate(String text) {
        Matcher matcher = DATE.matcher(text);
        while (matcher.find()) {
            LocalDate date = date(matcher.group(1), matcher.group(2), matcher.group(3));
            if (date != null) return date;
        }
        return null;
    }

    private LocalDate findDate(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? date(matcher.group(1), matcher.group(2), matcher.group(3)) : null;
    }

    private LocalDate date(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }

    private String firstLines(String text, int limit) {
        if (text == null) return "";
        return String.join("\n", text.lines().limit(limit).toList());
    }

    private String findGroup(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).strip() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
