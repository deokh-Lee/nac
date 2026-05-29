package com.saltlux.nac.activity;

public record ActivityExtractAllResult(
        String transferYear,
        String prodYear,
        int batchSize,
        int maxLoop,
        boolean retryFail,
        int loopCount,
        int totalTargetCount,
        int totalSuccessCount,
        int totalFailCount,
        boolean completed
) {
}
