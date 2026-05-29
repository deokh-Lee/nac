package com.saltlux.nac.subject;

public record SubjectExtractAllResult(
        String transferYear,
        String prodYear,
        int batchSize,
        int maxLoop,
        boolean retryFail,
        int loopCount,
        int totalTargetCount,
        SubjectExtractItemResult policy,
        SubjectExtractItemResult event,
        SubjectExtractItemResult activity,
        boolean completed
) {
}
