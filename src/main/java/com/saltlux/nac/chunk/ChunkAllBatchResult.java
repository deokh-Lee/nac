package com.saltlux.nac.chunk;

public record ChunkAllBatchResult(
        String transferYear,
        Integer dataYear,
        int batchSize,
        int maxLoop,
        boolean recreate,
        int chunkSize,
        int overlapSize,
        int loopCount,
        int totalTargetCount,
        int totalSuccessCount,
        int totalFailCount,
        int totalChunkCount,
        boolean completed
) {
}
