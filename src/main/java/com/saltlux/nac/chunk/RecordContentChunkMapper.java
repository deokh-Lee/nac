package com.saltlux.nac.chunk;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RecordContentChunkMapper {

    List<ChunkSourceDocument> findChunkTargets(@Param("transferYear") String transferYear,
                                               @Param("dataYear") Integer dataYear,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset,
                                               @Param("recreate") boolean recreate);

    int deleteChunksForDocument(ChunkSourceDocument document);

    int insertChunk(RecordContentChunk chunk);
}
