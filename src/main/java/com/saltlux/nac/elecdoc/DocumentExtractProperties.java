package com.saltlux.nac.elecdoc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.extract")
public class DocumentExtractProperties {

    private String basePath = "/data/file-data/elec_doc";
    private String defaultTransferYear = "2023";
    private int batchSize = 100;

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getDefaultTransferYear() {
        return defaultTransferYear;
    }

    public void setDefaultTransferYear(String defaultTransferYear) {
        this.defaultTransferYear = defaultTransferYear;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
