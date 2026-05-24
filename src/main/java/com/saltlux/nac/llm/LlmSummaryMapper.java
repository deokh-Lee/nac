package com.saltlux.nac.llm;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LlmSummaryMapper {

    List<LlmSummaryTarget> findSummaryTargets(@Param("transferYear") String transferYear,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);
}
