package com.saltlux.nac.event;

public record EventExtractAllResult(
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
