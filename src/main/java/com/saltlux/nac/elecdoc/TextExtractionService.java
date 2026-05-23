package com.saltlux.nac.elecdoc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

@Service
public class TextExtractionService {

    private static final int WRITE_LIMIT = -1;

    private final Tika tika = new Tika();
    private final AutoDetectParser parser = new AutoDetectParser();

    public TextExtractionResult extract(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Path is not a file: " + path);
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, path.getFileName().toString());

            BodyContentHandler handler = new BodyContentHandler(WRITE_LIMIT);
            parser.parse(inputStream, handler, metadata);

            String contents = normalize(handler.toString());
            String detectedType = tika.detect(path);
            boolean hasContents = contents != null && !contents.isBlank();

            return new TextExtractionResult(contents, detectedType, hasContents);
        } catch (Exception e) {
            throw new IllegalStateException("Text extraction failed. file=" + path + ", message=" + e.getMessage(), e);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\u0000", "")
                .replaceAll("[ \t\x0B\f\r]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
