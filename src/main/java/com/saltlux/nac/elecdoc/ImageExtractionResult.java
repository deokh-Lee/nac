package com.saltlux.nac.elecdoc;

public record ImageExtractionResult(
        String tagText,
        String imgDatasJson
) {
    public static ImageExtractionResult empty() {
        return new ImageExtractionResult("", "[]");
    }
}
