package com.saltlux.nac.subject;

public record SubjectExtractResult(
        String transferYear,
        String prodYear,
        int requestedCount,
        int offset,
        boolean retryFail,
        int targetCount,
        int workerCount,
        SubjectExtractItemResult policy,
        SubjectExtractItemResult event,
        SubjectExtractItemResult activity
) {
}
