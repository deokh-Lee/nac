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

    List<PolicyExtractTarget> findPolicyExtractSampleTargets(@Param("transferYear") String transferYear,
                                                             @Param("prodYear") String prodYear,
                                                             @Param("departments") List<String> departments,
                                                             @Param("limit") int limit,
                                                             @Param("offset") int offset,
                                                             @Param("retryFail") boolean retryFail);

    List<PolicyExtractTarget> findPolicyDepartmentTargets(@Param("transferYear") String transferYear,
                                                          @Param("prodYear") String prodYear,
                                                          @Param("departments") List<String> departments,
                                                          @Param("limit") int limit,
                                                          @Param("offset") int offset);

    List<PolicyExtractTarget> findPolicyDepartmentFileTargets(@Param("transferYear") String transferYear,
                                                              @Param("prodYear") String prodYear,
                                                              @Param("departments") List<String> departments,
                                                              @Param("limit") int limit,
                                                              @Param("offset") int offset);

    List<PolicyExtractTarget> findPolicyDepartmentFileItemTargets(@Param("transferYear") String transferYear,
                                                                  @Param("prodYear") String prodYear,
                                                                  @Param("rcRfileNo") String rcRfileNo);

    List<PolicyExtractTarget> findPolicyDepartmentUnmappedTargets(@Param("transferYear") String transferYear,
                                                                  @Param("prodYear") String prodYear,
                                                                  @Param("departments") List<String> departments,
                                                                  @Param("limit") int limit,
                                                                  @Param("offset") int offset);

    List<PolicyExtractTarget> findPolicyDepartmentItemCompareTargets(@Param("transferYear") String transferYear,
                                                                     @Param("prodYear") String prodYear,
                                                                     @Param("departments") List<String> departments,
                                                                     @Param("limit") int limit,
                                                                     @Param("offset") int offset);

    int updatePolicyExtractSuccess(@Param("target") PolicyExtractTarget target,
                                   @Param("result") PolicyExtractResponse result);

    int updatePolicyDepartmentFileSuccess(@Param("target") PolicyExtractTarget target,
                                          @Param("result") PolicyExtractResponse result);

    int updatePolicyExtractFail(@Param("target") PolicyExtractTarget target,
                                @Param("errorMessage") String errorMessage);
}
