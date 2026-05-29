package com.saltlux.nac.subject;

public record SubjectExtractItemResult(
        String subjectType,
        String endpoint,
        int targetCount,
        int successCount,
        int failCount
) {
}
