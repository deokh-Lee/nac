package com.saltlux.nac.elecdoc;

public record LlmSummaryBatchResult(
        String transferYear,
        int requestedCount,
        int targetCount,
        int workerCount,
        int perWorkerSize,
        int successCount,
        int failCount
) {
}
