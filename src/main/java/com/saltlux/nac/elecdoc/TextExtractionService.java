package com.saltlux.nac.elecdoc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

@Service
public class TextExtractionService {

    private static final int WRITE_LIMIT = -1;
    private static final String PDF_MEDIA_TYPE = "application/pdf";

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
            PDFTextStripper stripper = new PDFTextStripper();

            // PDFBox 내부적으로는 페이지 순회가 필요하지만, 결과는 페이지별로 나누지 않고 하나의 문자열로 합칩니다.
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            stripper.setSortByPosition(true);

            // 페이지 경계에서 form feed 같은 구분자가 들어가면 전체 문맥이 끊길 수 있으므로 제거합니다.
            stripper.setPageSeparator("\n");

            String contents = normalizeForSingleDocument(stripper.getText(document));
            boolean hasContents = contents != null && !contents.isBlank();
            return new TextExtractionResult(contents, detectedType, hasContents);
        }
    }

    private TextExtractionResult extractByTika(Path path, String detectedType) throws Exception {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, path.getFileName().toString());

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

        return value
                .replace("\u0000", "")
                .replace("\f", "\n")
                .replaceAll("[ \t\x0B\f\r]+", " ")
                .replaceAll("(?m)^\s*[-–—]?\s*\d+\s*[-–—]?\s*$", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
