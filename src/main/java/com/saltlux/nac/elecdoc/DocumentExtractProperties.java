package com.saltlux.nac.elecdoc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.extract")
public class DocumentExtractProperties {

    private String basePath = "/data/file-data/elec_doc";
    private String imageBasePath = "/data/file-data/elec_doc/images";
    private String defaultTransferYear = "2023";
    private int batchSize = 100;
    private int threadCount = 4;
    private int chunkSize = 25;
    private int fileTimeoutSeconds = 300;

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getImageBasePath() {
        return imageBasePath;
    }

    public void setImageBasePath(String imageBasePath) {
        this.imageBasePath = imageBasePath;
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

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getFileTimeoutSeconds() {
        return fileTimeoutSeconds;
    }

    public void setFileTimeoutSeconds(int fileTimeoutSeconds) {
        this.fileTimeoutSeconds = fileTimeoutSeconds;
    }
}
