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
        String fileName = resolveFileName(document);

        if (StringUtils.hasText(document.getOrgFilePath())) {
            return resolveByOrgFilePath(document.getOrgFilePath(), fileName);
        }

        String transferYear = StringUtils.hasText(document.getTransferYear())
                ? document.getTransferYear()
                : properties.getDefaultTransferYear();

        return Path.of(
                properties.getBasePath(),
                transferYear,
                document.getRcRfileNo(),
                document.getRcRitemNo(),
                fileName
        );
    }

    public String resolveFileName(CnElecDoc document) {
        return document.getSaveFileName();
    }

    private Path resolveByOrgFilePath(String orgFilePath, String fileName) {
        String normalizedPath = orgFilePath.replace("\\", "/");

        if (normalizedPath.endsWith("/")) {
            return Path.of(normalizedPath, fileName);
        }

        if (StringUtils.hasText(fileName) && normalizedPath.endsWith(fileName)) {
            return Path.of(normalizedPath);
        }

        return Path.of(normalizedPath, fileName);
    }
}
