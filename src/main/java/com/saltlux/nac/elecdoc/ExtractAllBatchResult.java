package com.saltlux.nac.elecdoc;

public record ExtractAllBatchResult(
        String transferYear,
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
