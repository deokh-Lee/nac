package com.saltlux.nac.elecdoc;

public record TextExtractionResult(
        String contents,
        String fileType,
        boolean hasContents
) {
}
