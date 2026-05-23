package com.saltlux.nac.elecdoc;

public record TextExtractionResult(
        String contents,
        String fileType,
        boolean hasContents,
        String imgDatas
) {
    public static TextExtractionResult withoutImages(String contents, String fileType, boolean hasContents) {
        return new TextExtractionResult(contents, fileType, hasContents, "[]");
    }
}
