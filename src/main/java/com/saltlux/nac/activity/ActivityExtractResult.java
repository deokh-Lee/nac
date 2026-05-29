package com.saltlux.nac.activity;

public record ActivityExtractResult(
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
