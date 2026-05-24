package com.saltlux.nac.elecdoc;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LlmSummaryMapper {

    List<LlmSummaryTarget> findSummaryTargets(@Param("transferYear") String transferYear,
                                               @Param("limit") int limit,
                                               @Param("retryFail") boolean retryFail);

    int updateSummarySuccess(@Param("target") LlmSummaryTarget target,
                             @Param("summary") String summary);

    int updateSummaryFail(@Param("target") LlmSummaryTarget target,
                          @Param("errorMessage") String errorMessage);
}
