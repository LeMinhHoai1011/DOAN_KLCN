package com.example.invoice.verification;

import com.example.invoice.dto.invoice.ExtractedFieldResponse.FieldLocation;
import com.example.invoice.verification.FieldVerifier.Evidence;
import com.example.invoice.verification.FieldVerifier.Kind;
import com.example.invoice.verification.FieldVerifier.Location;
import com.example.invoice.verification.FieldVerifier.Outcome;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a value in the stored OCR/PDF text. Word layout gives a location to highlight on the image;
 * raw text is the fallback (PDF text layers have no word boxes). Matching is deterministic.
 */
public final class OcrEvidenceFinder implements FieldVerifier.EvidenceFinder {
    /** Share of a text value's words that must appear in OCR for the evidence to count. */
    static final double TEXT_COVERAGE = 0.8;
    // No whitespace grouping: "10 100" must stay two numbers, never become evidence for 10100.
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?");

    private final String foldedText;
    private final Set<String> words;
    private final List<BigDecimal> numbers;
    private final String alnumText;
    private final BiFunction<String, String, List<FieldLocation>> locator;

    /** locator: (fieldName, candidateText) → locations in the word layout; may return an empty list. */
    public OcrEvidenceFinder(String rawText, BiFunction<String, String, List<FieldLocation>> locator) {
        this.foldedText = fold(rawText == null ? "" : rawText);
        this.words = new HashSet<>(Arrays.asList(foldedText.split("[^A-Z0-9]+")));
        this.alnumText = foldedText.replaceAll("[^A-Z0-9]", "");
        this.numbers = numbers(rawText == null ? "" : rawText);
        this.locator = locator == null ? (name, value) -> List.of() : locator;
    }

    @Override
    public Evidence find(String fieldKey, Kind kind, String value) {
        String trimmed = value.trim();
        String compact = fold(trimmed).replaceAll("[^A-Z0-9]", "");
        if (compact.length() <= 2) return Evidence.unavailable(FieldVerifier.TOO_SHORT_TO_MATCH);
        String fieldName = fieldKey.contains(".") ? fieldKey.substring(fieldKey.lastIndexOf('.') + 1) : fieldKey;
        for (String candidate : variants(kind, trimmed)) {
            List<FieldLocation> found = locator.apply(fieldName, candidate);
            if (!found.isEmpty()) {
                FieldLocation at = found.getFirst();
                double score = at.matchConfidence() == null ? 1d : at.matchConfidence().doubleValue();
                return new Evidence(Outcome.PASS, score, "OCR_WORDS", candidate,
                        new Location(at.page(), at.x(), at.y(), at.width(), at.height(), at.pageWidth(), at.pageHeight()));
            }
        }
        return switch (kind) {
            case AMOUNT, QUANTITY -> numberEvidence(trimmed);
            case DATE -> dateEvidence(trimmed);
            case TAX_CODE, INVOICE_SERIES -> alnumText.contains(compact) ? text(1d, trimmed) : Evidence.notFound();
            case INVOICE_NUMBER -> invoiceNumberEvidence(trimmed);
            default -> textEvidence(trimmed);
        };
    }

    private Evidence numberEvidence(String value) {
        BigDecimal target = FieldVerifier.parseAmount(value);
        if (target == null) return Evidence.notFound();
        for (BigDecimal number : numbers) if (number.compareTo(target) == 0) return text(1d, FieldVerifier.plain(target));
        return Evidence.notFound();
    }

    private Evidence dateEvidence(String value) {
        LocalDate date = FieldVerifier.parseDate(value);
        if (date == null) return Evidence.notFound();
        String d = "0?" + date.getDayOfMonth(), m = "0?" + date.getMonthValue(), y = String.valueOf(date.getYear());
        Pattern numeric = Pattern.compile("(?<!\\d)" + d + "\\s*[/.\\-]\\s*" + m + "\\s*[/.\\-]\\s*" + y + "(?!\\d)");
        // "Ngày 15 tháng 03 năm 2024", optionally with bilingual labels in parentheses.
        Pattern words = Pattern.compile("NGAY\\s*(?:\\([^)]{0,12}\\))?\\s*" + d + "\\s*THANG\\s*(?:\\([^)]{0,12}\\))?\\s*" + m
                + "\\s*NAM\\s*(?:\\([^)]{0,12}\\))?\\s*" + y + "(?!\\d)");
        for (Pattern pattern : List.of(numeric, words)) {
            Matcher matcher = pattern.matcher(foldedText);
            if (matcher.find()) return text(1d, matcher.group());
        }
        return Evidence.notFound();
    }

    /** Printed numbers often carry leading zeros (0000123) that the model drops or keeps. */
    private Evidence invoiceNumberEvidence(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) return textEvidence(value);
        BigDecimal target = new BigDecimal(digits);
        Matcher run = Pattern.compile("\\d+").matcher(foldedText);
        while (run.find()) if (new BigDecimal(run.group()).compareTo(target) == 0) return text(1d, run.group());
        return Evidence.notFound();
    }

    private Evidence textEvidence(String value) {
        List<String> tokens = Arrays.stream(fold(value).split("[^A-Z0-9]+")).filter(t -> t.length() >= 2).toList();
        if (tokens.isEmpty()) return Evidence.unavailable(FieldVerifier.TOO_SHORT_TO_MATCH);
        long found = tokens.stream().filter(words::contains).count();
        double coverage = found / (double) tokens.size();
        return coverage >= TEXT_COVERAGE ? text(coverage, value)
                : new Evidence(Outcome.FAIL, coverage, "OCR_TEXT_PARTIAL", null, null);
    }

    private static Evidence text(double score, String matched) { return new Evidence(Outcome.PASS, score, "OCR_TEXT", matched, null); }

    private static List<String> variants(Kind kind, String value) {
        var result = new LinkedHashSet<String>();
        result.add(value);
        if (kind == Kind.DATE) {
            LocalDate date = FieldVerifier.parseDate(value);
            if (date != null) {
                result.add(String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear()));
                result.add(String.format("%02d-%02d-%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear()));
            }
        }
        if (kind == Kind.AMOUNT || kind == Kind.QUANTITY) {
            BigDecimal amount = FieldVerifier.parseAmount(value);
            if (amount != null) result.add(FieldVerifier.plain(amount));
        }
        return List.copyOf(result);
    }

    static List<BigDecimal> numbers(String text) {
        var result = new ArrayList<BigDecimal>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            result.add(new BigDecimal(token.replaceAll("[.,]", "")));
            Matcher decimal = Pattern.compile("^(.*)[.,](\\d{1,2})$").matcher(token);
            if (decimal.find()) result.add(new BigDecimal(decimal.group(1).replaceAll("[.,]", "") + "." + decimal.group(2)));
        }
        return result;
    }

    static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D').toUpperCase(Locale.ROOT);
    }
}
