package com.saltlux.nac.elecdoc;

public record DocumentImageContext(
        String transferYear,
        String rcRfileNo,
        String rcRitemNo,
        String fileName
) {
}
