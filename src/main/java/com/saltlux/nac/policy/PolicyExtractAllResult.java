package com.saltlux.nac.policy;

public record PolicyExtractAllResult(
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
