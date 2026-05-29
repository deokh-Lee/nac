package com.saltlux.nac.activity;

import com.saltlux.nac.policy.PolicyExtractTarget;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActivityExtractMapper {

    List<PolicyExtractTarget> findActivityExtractTargets(@Param("transferYear") String transferYear,
                                                         @Param("prodYear") String prodYear,
                                                         @Param("limit") int limit,
                                                         @Param("offset") int offset,
                                                         @Param("retryFail") boolean retryFail);

    int updateActivityExtractSuccess(@Param("target") PolicyExtractTarget target,
                                     @Param("result") ActivityExtractResponse result);

    int updateActivityExtractFail(@Param("target") PolicyExtractTarget target,
                                  @Param("errorMessage") String errorMessage);
}
