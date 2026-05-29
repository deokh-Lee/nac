package com.saltlux.nac.subject;

import com.saltlux.nac.policy.PolicyExtractTarget;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SubjectExtractMapper {

    List<PolicyExtractTarget> findSubjectExtractTargets(@Param("transferYear") String transferYear,
                                                        @Param("prodYear") String prodYear,
                                                        @Param("limit") int limit,
                                                        @Param("offset") int offset,
                                                        @Param("retryFail") boolean retryFail);
}
