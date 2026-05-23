package com.saltlux.nac.elecdoc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

@Service
public class TextExtractionService {

    private static final int WRITE_LIMIT = -1;
    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String NULL_CHARACTER = String.valueOf('\u0000');
    private static final Pattern INLINE_WHITESPACE_PATTERN = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern PAGE_NUMBER_LINE_PATTERN = Pattern.compile("(?m)^\\s*[-–—]?\\s*\\d+\\s*[-–—]?\\s*$");
    private static final Pattern MULTIPLE_BLANK_LINES_PATTERN = Pattern.compile("\\n{3,}");

    private final Tika tika = new Tika();
    private final AutoDetectParser parser = new AutoDetectParser();

    public TextExtractionResult extract(Path path) {
        validateFile(path);

        try {
            String detectedType = tika.detect(path);
            if (PDF_MEDIA_TYPE.equalsIgnoreCase(detectedType) || "pdf".equals(FileTypeUtils.extensionOf(path.getFileName().toString()))) {
                return extractPdfAsSingleText(path, detectedType);
            }
            return extractByTika(path, detectedType);
        } catch (Exception e) {
            throw new IllegalStateException("Text extraction failed. file=" + path + ", message=" + e.getMessage(), e);
        }
    }

    private TextExtractionResult extractPdfAsSingleText(Path path, String detectedType) throws Exception {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            ColumnAwarePdfTextExtractor extractor = new ColumnAwarePdfTextExtractor();
            String contents = normalizeForSingleDocument(extractor.extract(document));
            boolean hasContents = contents != null && !contents.isBlank();
            return new TextExtractionResult(contents, detectedType, hasContents);
        }
    }

    private TextExtractionResult extractByTika(Path path, String detectedType) throws Exception {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());

            BodyContentHandler handler = new BodyContentHandler(WRITE_LIMIT);
            parser.parse(inputStream, handler, metadata);

            String contents = normalizeForSingleDocument(handler.toString());
            boolean hasContents = contents != null && !contents.isBlank();

            return new TextExtractionResult(contents, detectedType, hasContents);
        }
    }

    private void validateFile(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Path is not a file: " + path);
        }
    }

    private String normalizeForSingleDocument(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value
                .replace(NULL_CHARACTER, "")
                .replace('\f', '\n');

        normalized = INLINE_WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = PAGE_NUMBER_LINE_PATTERN.matcher(normalized).replaceAll("");
        normalized = MULTIPLE_BLANK_LINES_PATTERN.matcher(normalized).replaceAll("\n\n");

        return normalized.trim();
    }
}
