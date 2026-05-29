package com.saltlux.nac.policy;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PolicyExtractMapper {

    List<PolicyExtractTarget> findPolicyExtractTargets(@Param("transferYear") String transferYear,
                                                       @Param("prodYear") String prodYear,
                                                       @Param("limit") int limit,
                                                       @Param("offset") int offset,
                                                       @Param("retryFail") boolean retryFail);

    int updatePolicyExtractSuccess(@Param("target") PolicyExtractTarget target,
                                   @Param("result") PolicyExtractResponse result);

    int updatePolicyExtractFail(@Param("target") PolicyExtractTarget target,
                                @Param("errorMessage") String errorMessage);
}
