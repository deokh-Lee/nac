package com.saltlux.nac.event;

import com.saltlux.nac.policy.PolicyExtractTarget;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventExtractMapper {

    List<PolicyExtractTarget> findEventExtractTargets(@Param("transferYear") String transferYear,
                                                      @Param("prodYear") String prodYear,
                                                      @Param("limit") int limit,
                                                      @Param("offset") int offset,
                                                      @Param("retryFail") boolean retryFail);

    int updateEventExtractSuccess(@Param("target") PolicyExtractTarget target,
                                  @Param("result") EventExtractResponse result);

    int updateEventExtractFail(@Param("target") PolicyExtractTarget target,
                               @Param("errorMessage") String errorMessage);
}
