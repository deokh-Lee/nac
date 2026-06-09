package com.saltlux.nac.chunk;

public record ChunkOptions(
        int chunkSize,
        int overlapSize
) {
}
