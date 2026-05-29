package com.saltlux.nac.subject;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SubjectPolicyMapper {

    List<SubjectPolicyCandidate> findSubjectCandidates(@Param("clsCd") String clsCd,
                                                       @Param("productionDate") String productionDate,
                                                       @Param("productionYear") String productionYear);
}
