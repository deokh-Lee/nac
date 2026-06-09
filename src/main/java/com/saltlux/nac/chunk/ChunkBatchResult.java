package com.saltlux.nac.chunk;

public record ChunkBatchResult(
        String transferYear,
        Integer dataYear,
        int requestedLimit,
        int offset,
        boolean recreate,
        int chunkSize,
        int overlapSize,
        int targetCount,
        int successCount,
        int failCount,
        int chunkCount
) {
}
