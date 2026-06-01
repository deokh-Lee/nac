package com.saltlux.nac.progress;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LlmExtractProgressMapper {

    int insertRun(LlmExtractRun run);

    int finishRun(@Param("runId") Long runId,
                  @Param("status") String status,
                  @Param("loopCount") int loopCount,
                  @Param("totalTargetCount") int totalTargetCount,
                  @Param("totalSuccessCount") int totalSuccessCount,
                  @Param("totalFailCount") int totalFailCount,
                  @Param("errorMsg") String errorMsg);

    int updateRunProgress(@Param("runId") Long runId,
                          @Param("loopCount") int loopCount,
                          @Param("totalTargetCount") int totalTargetCount,
                          @Param("totalSuccessCount") int totalSuccessCount,
                          @Param("totalFailCount") int totalFailCount);

    int insertItemLog(LlmExtractItemLog itemLog);
}
