package com.example.invoice.evaluation;

import com.example.invoice.config.AiProperties;
import com.example.invoice.service.OcrPageResult;
import com.example.invoice.service.PdfImageConversionService;
import com.example.invoice.service.Tess4jOcrService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Produces strategy R evidence for tools/evaluation/prepare-bundle.mjs. Offline only: no Spring context,
 * no database, no AI provider. Plain Tess4J (no OpenCV preprocessing, no vision fallback) + rules.
 *
 * Usage: BaselineEvidenceRunner <source-dir> <run-id> <code-revision> [rules.json]
 * Writes runs/<run-id>/{config.json,evidence/*.json,run-entry.json} under source-dir; never edits manifest.json.
 */
public final class BaselineEvidenceRunner {
    private BaselineEvidenceRunner() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: BaselineEvidenceRunner <source-dir> <run-id> <code-revision> [rules.json]");
            System.exit(1);
        }
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path source = Path.of(args[0]).toRealPath();
        String runId = args[1], codeRevision = args[2];
        if (!runId.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid run id");
        JsonNode rules = args.length == 4 ? json.readTree(Files.readAllBytes(Path.of(args[3]))) : json.createObjectNode();
        Map<String, List<String>> typeKeywords = keywords(json, rules.path("typeKeywords"));
        Map<String, List<String>> categoryKeywords = keywords(json, rules.path("categoryKeywords"));
        var extractor = new RuleBasedBaselineExtractor(typeKeywords, categoryKeywords);

        AiProperties properties = new AiProperties();
        properties.getOcr().setDataPath(System.getenv().getOrDefault("TESSDATA_PATH", "./tessdata"));
        properties.getOcr().setLanguage(System.getenv().getOrDefault("OCR_LANGUAGE", "vie+eng"));
        var ocr = new Tess4jOcrService(properties);
        var pdf = new PdfImageConversionService(properties);

        JsonNode manifest = json.readTree(Files.readAllBytes(source.resolve("manifest.json")));
        int schemaVersion = manifest.path("schemaVersion").asInt();
        Path output = source.resolve("runs").resolve(runId);
        if (Files.exists(output)) throw new IllegalStateException("Run output already exists: " + output);
        Files.createDirectories(output.resolve("evidence"));

        Instant startedAt = Instant.now();
        var settings = new LinkedHashMap<String, Object>();
        settings.put("extractorVersion", RuleBasedBaselineExtractor.VERSION);
        settings.put("ocrEngine", "tess4j");
        settings.put("ocrLanguage", properties.getOcr().getLanguage());
        settings.put("ocrPreprocessing", "none");
        settings.put("pdfTextLayerFirst", true);
        settings.put("pdfDpi", properties.getPdf().getDpi());
        settings.put("llm", "none");
        settings.put("typeKeywords", typeKeywords.isEmpty() ? RuleBasedBaselineExtractor.DEFAULT_TYPE_KEYWORDS : typeKeywords);
        settings.put("categoryKeywords", categoryKeywords);
        var config = new LinkedHashMap<String, Object>();
        config.put("strategy", "R");
        config.put("model", RuleBasedBaselineExtractor.VERSION);
        // The "model" of a rule baseline is its rule set: hash it so changed rules are a different model.
        config.put("modelDigest", EvaluationService.hash(json.writeValueAsBytes(settings)));
        config.put("environment", "java " + System.getProperty("java.version") + "; " + System.getProperty("os.name")
                + "; tess4j " + properties.getOcr().getLanguage());
        config.put("codeRevision", codeRevision);
        config.put("settings", settings);
        Files.write(output.resolve("config.json"), json.writeValueAsBytes(config));

        var results = new ArrayList<Map<String, String>>();
        int failed = 0;
        for (JsonNode sample : manifest.path("samples")) {
            String sampleId = sample.path("id").asText();
            byte[] asset = Files.readAllBytes(resolveInside(source, sample.path("assetFile").asText()));
            var evidence = new LinkedHashMap<String, Object>();
            evidence.put("sampleId", sampleId);
            evidence.put("assetSha256", EvaluationService.hash(asset));
            long started = System.nanoTime();
            try {
                String text = recognize(asset, ocr, pdf);
                var prediction = extractor.extract(text);
                evidence.put("outcome", "SUCCESS");
                evidence.put("failureCode", null);
                evidence.put("durationMs", (System.nanoTime() - started) / 1_000_000);
                evidence.put("visionFallback", false);
                evidence.put("attemptCount", 1);
                evidence.put("ocrText", text.length() > 20_000 ? text.substring(0, 20_000) : text);
                evidence.put("ocrFields", null);
                evidence.put("fields", prediction.fields());
                evidence.put("items", null); // R does not reconstruct item tables; reported as missing cells.
                evidence.put("predictedType", prediction.predictedType());
                if (schemaVersion == 2) evidence.put("predictedCategory",
                        sample.hasNonNull("categoryTaxonomy") ? prediction.predictedCategory() : null);
            } catch (RuntimeException exception) {
                failed++;
                evidence.put("outcome", "FAILED");
                evidence.put("failureCode", "BASELINE_OCR_FAILED");
                evidence.put("durationMs", (System.nanoTime() - started) / 1_000_000);
                evidence.put("visionFallback", false);
                evidence.put("attemptCount", 1);
                evidence.put("ocrText", null);
                evidence.put("ocrFields", null);
                evidence.put("fields", null);
                evidence.put("items", null);
                evidence.put("predictedType", null);
                if (schemaVersion == 2) evidence.put("predictedCategory", null);
            }
            String file = "runs/" + runId + "/evidence/" + EvaluationService.hash(sampleId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 16) + ".json";
            Files.write(source.resolve(file), json.writeValueAsBytes(evidence));
            results.add(Map.of("evidenceFile", file));
        }
        var entry = new LinkedHashMap<String, Object>();
        entry.put("id", runId);
        entry.put("startedAt", startedAt.toString());
        entry.put("configFile", "runs/" + runId + "/config.json");
        entry.put("results", results);
        Files.write(output.resolve("run-entry.json"), json.writeValueAsBytes(entry));
        System.out.println("Strategy R: " + results.size() + " samples, " + failed + " failed. Add runs/" + runId
                + "/run-entry.json to manifest.json \"runs\", then run prepare-bundle.mjs.");
    }

    private static String recognize(byte[] asset, Tess4jOcrService ocr, PdfImageConversionService pdf) {
        boolean isPdf = asset.length > 4 && asset[0] == '%' && asset[1] == 'P' && asset[2] == 'D' && asset[3] == 'F';
        if (!isPdf) return requireText(ocr.recognize(asset, 1));
        var textPages = pdf.extractTextPages(asset);
        if (textPages.stream().allMatch(PdfImageConversionService.PdfTextPage::usable))
            return String.join("\n", textPages.stream().map(PdfImageConversionService.PdfTextPage::text).toList());
        var pages = new ArrayList<String>();
        for (var page : pdf.convert(asset)) pages.add(requireText(ocr.recognize(page.bytes(), page.pageNumber())));
        return String.join("\n", pages);
    }

    private static String requireText(OcrPageResult page) {
        if (page.text() == null || page.text().isBlank()) throw new IllegalStateException("OCR returned no text");
        return page.text();
    }

    private static Path resolveInside(Path root, String relative) throws java.io.IOException {
        Path path = root.resolve(relative).toRealPath();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Asset outside source directory");
        return path;
    }

    private static Map<String, List<String>> keywords(ObjectMapper json, JsonNode node) {
        var map = new LinkedHashMap<String, List<String>>();
        node.properties().forEach(e -> map.put(e.getKey(), json.convertValue(e.getValue(), json.getTypeFactory()
                .constructCollectionType(List.class, String.class))));
        return map;
    }
}
