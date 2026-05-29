package com.saltlux.nac.policy;

public record PolicyExtractResult(
        String transferYear,
        String prodYear,
        int requestedCount,
        int offset,
        boolean retryFail,
        int targetCount,
        int workerCount,
        int successCount,
        int failCount
) {
}
