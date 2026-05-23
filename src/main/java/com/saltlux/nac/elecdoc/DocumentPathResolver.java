package com.saltlux.nac.elecdoc;

import java.nio.file.Path;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentPathResolver {

    private final DocumentExtractProperties properties;

    public DocumentPathResolver(DocumentExtractProperties properties) {
        this.properties = properties;
    }

    public Path resolve(CnElecDoc document) {
        String transferYear = StringUtils.hasText(document.getTransferYear())
                ? document.getTransferYear()
                : properties.getDefaultTransferYear();

        String fileName = resolveFileName(document);

        return Path.of(
                properties.getBasePath(),
                transferYear,
                document.getRcRfileNo(),
                document.getRcRitemNo(),
                fileName
        );
    }

    public String resolveFileName(CnElecDoc document) {
        if (StringUtils.hasText(document.getSaveFileName())) {
            return document.getSaveFileName();
        }
        return document.getOrgFileName();
    }
}
