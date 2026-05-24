package com.saltlux.nac.elecdoc;

import java.util.Properties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class OfficialDocumentMetadataInterceptor implements Interceptor {

    private static final String UPSERT_ID = "com.saltlux.nac.elecdoc.ElecDocMapper.upsertExtractDocument";

    private final OfficialDocumentPostProcessor postProcessor;
    private final JdbcTemplate jdbcTemplate;

    public OfficialDocumentMetadataInterceptor(OfficialDocumentPostProcessor postProcessor,
                                               JdbcTemplate jdbcTemplate) {
        this.postProcessor = postProcessor;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (!isTargetUpsert(args)) {
            return invocation.proceed();
        }

        ExtractElecDoc extract = (ExtractElecDoc) args[1];
        postProcessor.apply(extract);

        Object result = invocation.proceed();
        updateOfficialColumns(extract);
        return result;
    }

    private boolean isTargetUpsert(Object[] args) {
        if (args == null || args.length < 2) {
            return false;
        }
        if (!(args[0] instanceof MappedStatement mappedStatement)) {
            return false;
        }
        return UPSERT_ID.equals(mappedStatement.getId()) && args[1] instanceof ExtractElecDoc;
    }

    private void updateOfficialColumns(ExtractElecDoc extract) {
        if (!hasOfficialMetadata(extract)) {
            return;
        }

        jdbcTemplate.update(
                "UPDATE EXTRACT_ELEC_DOC "
                        + "SET IMPLEMENT_ORG = ?, IMPLEMENT_DATE = ?, RECEIPT_ORG = ?, RECEIPT_DATE = ?, UPDATE_DATE = NOW() "
                        + "WHERE FILE_NAME = ? AND RC_RFILE_NO = ? AND RC_RITEM_NO = ? AND ZIP_SEQ = ?",
                extract.getImplementOrg(),
                extract.getImplementDate(),
                extract.getReceiptOrg(),
                extract.getReceiptDate(),
                extract.getFileName(),
                extract.getRcRfileNo(),
                extract.getRcRitemNo(),
                extract.getZipSeq()
        );
    }

    private boolean hasOfficialMetadata(ExtractElecDoc extract) {
        if (extract == null) {
            return false;
        }
        return StringUtils.hasText(extract.getImplementOrg())
                || StringUtils.hasText(extract.getImplementDate())
                || StringUtils.hasText(extract.getReceiptOrg())
                || StringUtils.hasText(extract.getReceiptDate());
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no properties
    }
}
