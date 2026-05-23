package com.saltlux.nac.elecdoc;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ElecDocMapper {

    List<CnElecDoc> findTargetDocuments(@Param("transferYear") String transferYear,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset,
                                         @Param("retryFail") boolean retryFail);

    int upsertExtractDocument(ExtractElecDoc extractElecDoc);
}
