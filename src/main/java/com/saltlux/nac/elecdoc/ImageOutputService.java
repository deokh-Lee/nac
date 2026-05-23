package com.saltlux.nac.elecdoc;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ImageOutputService {

    private final DocumentExtractProperties properties;

    public ImageOutputService(DocumentExtractProperties properties) {
        this.properties = properties;
    }

    public String saveAsPng(DocumentImageContext context, int imageSeq, byte[] imageBytes) throws IOException {
        if (context == null || imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            return null;
        }

        Path outputDirectory = resolveOutputDirectory(context);
        Files.createDirectories(outputDirectory);

        Path outputFile = outputDirectory.resolve("img" + imageSeq + ".png");
        ImageIO.write(image, "png", outputFile.toFile());
        return outputFile.toString();
    }

    public String toJson(Map<String, String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');

        boolean first = true;
        for (Map.Entry<String, String> entry : imagePaths.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append('"').append(escapeJson(entry.getKey())).append('"');
            sb.append(": ");
            sb.append('"').append(escapeJson(entry.getValue())).append('"');
            first = false;
        }

        sb.append('}');
        return sb.toString();
    }

    public ImageExtractionResult toImageExtractionResult(LinkedHashMap<String, String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return ImageExtractionResult.empty();
        }

        StringBuilder tagText = new StringBuilder();
        for (String imageKey : imagePaths.keySet()) {
            tagText.append('\n').append('<').append(imageKey).append("/>");
        }

        return new ImageExtractionResult(tagText.toString(), toJson(imagePaths));
    }

    private Path resolveOutputDirectory(DocumentImageContext context) {
        String transferYear = StringUtils.hasText(context.transferYear())
                ? context.transferYear()
                : properties.getDefaultTransferYear();

        return Path.of(
                properties.getImageBasePath(),
                transferYear,
                nullToEmpty(context.rcRfileNo()),
                nullToEmpty(context.rcRitemNo()),
                removeExtension(context.fileName())
        );
    }

    private String removeExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "unknown";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
