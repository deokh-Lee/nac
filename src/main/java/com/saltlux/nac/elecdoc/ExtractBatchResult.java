package com.saltlux.nac.elecdoc;

public record ExtractBatchResult(
        String transferYear,
        int requestedLimit,
        int offset,
        int targetCount,
        int successCount,
        int failCount
) {
}
