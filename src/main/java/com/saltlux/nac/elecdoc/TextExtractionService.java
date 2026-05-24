package com.saltlux.nac.elecdoc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.util.RecordFormatException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private static final int WRITE_LIMIT = -1;
    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String HWP_MEDIA_TYPE = "application/x-hwp";
    private static final String HWP_TIKA_FALLBACK_MEDIA_TYPE = "application/x-hwp+tika-fallback";
    private static final String HWP_PARAGRAPH_ERROR_MESSAGE = "This is not paragraph.";
    private static final String NULL_CHARACTER = String.valueOf('\u0000');
    private static final Pattern INLINE_WHITESPACE_PATTERN = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern PAGE_NUMBER_LINE_PATTERN = Pattern.compile("(?m)^\\s*[-–—]?\\s*\\d+\\s*[-–—]?\\s*$");
    private static final Pattern MULTIPLE_BLANK_LINES_PATTERN = Pattern.compile("\\n{3,}");

    private final Tika tika = new Tika();
    private final AutoDetectParser parser = new AutoDetectParser();
    private final HwpTextExtractionService hwpTextExtractionService;
    private final HwpxTextExtractionService hwpxTextExtractionService;

    public TextExtractionService(HwpTextExtractionService hwpTextExtractionService,
                                 HwpxTextExtractionService hwpxTextExtractionService) {
        this.hwpTextExtractionService = hwpTextExtractionService;
        this.hwpxTextExtractionService = hwpxTextExtractionService;
    }

    public TextExtractionResult extract(Path path) {
        return extract(path, null);
    }

    public TextExtractionResult extract(Path path, DocumentImageContext imageContext) {
        validateFile(path);

        try {
            String extension = FileTypeUtils.extensionOf(path.getFileName().toString());
            String detectedType = tika.detect(path);

            if ("hwp".equals(extension)) {
                return extractHwpAsSingleText(path, imageContext);
            }
            if ("hwpx".equals(extension)) {
                return extractHwpxAsSingleText(path, imageContext);
            }
            if (PDF_MEDIA_TYPE.equalsIgnoreCase(detectedType) || "pdf".equals(extension)) {
                return extractPdfAsSingleText(path, detectedType);
            }
            return extractByTika(path, detectedType);
        } catch (DocumentExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentExtractionException(
                    "EXTRACT_FAILED",
                    "Text extraction failed. file=" + path + ", message=" + safeMessage(e),
                    e
            );
        }
    }

    private TextExtractionResult extractHwpAsSingleText(Path path, DocumentImageContext imageContext) throws Exception {
        try {
            TextExtractionResult result = hwpTextExtractionService.extract(path, imageContext);
            String contents = normalizeForSingleDocument(result.contents());
            boolean hasContents = contents != null && !contents.isBlank();
            return new TextExtractionResult(contents, result.fileType(), hasContents, result.imgDatas());
        } catch (Exception e) {
            if (isHwpParagraphParseError(e)) {
                log.warn("HWP parser failed with paragraph error. Retry with Tika. file={}, error={}", path, safeMessage(rootCause(e)));
                return extractHwpByTikaFallback(path);
            }
            throw e;
        }
    }

    private TextExtractionResult extractHwpByTikaFallback(Path path) throws Exception {
        TextExtractionResult result = extractByTika(path, HWP_MEDIA_TYPE);
        String contents = normalizeForSingleDocument(result.contents());
        boolean hasContents = contents != null && !contents.isBlank();
        return new TextExtractionResult(contents, HWP_TIKA_FALLBACK_MEDIA_TYPE, hasContents, "[]");
    }

    private TextExtractionResult extractHwpxAsSingleText(Path path, DocumentImageContext imageContext) throws Exception {
        TextExtractionResult result = hwpxTextExtractionService.extract(path, imageContext);
        String contents = normalizeForSingleDocument(result.contents());
        boolean hasContents = contents != null && !contents.isBlank();
        return new TextExtractionResult(contents, result.fileType(), hasContents, result.imgDatas());
    }

    private TextExtractionResult extractPdfAsSingleText(Path path, String detectedType) throws Exception {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            ColumnAwarePdfTextExtractor extractor = new ColumnAwarePdfTextExtractor();
            String contents = normalizeForSingleDocument(extractor.extract(document));
            boolean hasContents = contents != null && !contents.isBlank();
            return TextExtractionResult.withoutImages(contents, detectedType, hasContents);
        } catch (Exception e) {
            throw new DocumentExtractionException(
                    "PDF_PARSE_FAILED",
                    "PDF parse failed. file=" + path + ", message=" + safeMessage(e),
                    e
            );
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

            return TextExtractionResult.withoutImages(contents, detectedType, hasContents);
        } catch (RecordFormatException e) {
            throw new DocumentExtractionException(
                    "OFFICE_RECORD_FORMAT_FAILED",
                    "Office metadata/body parse failed. file=" + path + ", message=" + safeMessage(e),
                    e
            );
        } catch (TikaException | SAXException e) {
            throw new DocumentExtractionException(
                    "TIKA_PARSE_FAILED",
                    "Tika parse failed. file=" + path + ", message=" + safeMessage(e),
                    e
            );
        } catch (IllegalArgumentException e) {
            throw new DocumentExtractionException(
                    "INVALID_DOCUMENT_FORMAT",
                    "Invalid document format. file=" + path + ", message=" + safeMessage(e),
                    e
            );
        }
    }

    private void validateFile(Path path) {
        if (!Files.exists(path)) {
            throw new DocumentExtractionException("FILE_NOT_FOUND", "File not found: " + path, null);
        }
        if (!Files.isRegularFile(path)) {
            throw new DocumentExtractionException("NOT_REGULAR_FILE", "Path is not a file: " + path, null);
        }
    }

    private boolean isHwpParagraphParseError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(HWP_PARAGRAPH_ERROR_MESSAGE)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Throwable rootCause(Throwable e) {
        Throwable current = e;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? e : current;
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

    private String safeMessage(Throwable e) {
        if (e == null || e.getMessage() == null) {
            return e == null ? "" : e.getClass().getSimpleName();
        }
        return e.getMessage();
    }
}
