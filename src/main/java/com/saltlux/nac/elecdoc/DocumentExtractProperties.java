package com.saltlux.nac.elecdoc;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.extract")
public class DocumentExtractProperties {

    private String basePath = "/data/file-data/elec_doc";
    private String imageBasePath = "/data/file-data/elec_doc/images";
    private String defaultTransferYear = "2023";
    private int batchSize = 100;
    private int threadCount = 4;
    private int chunkSize = 25;
    private int fileTimeoutSeconds = 60;
    private Llm llm = new Llm();

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    public String getImageBasePath() { return imageBasePath; }
    public void setImageBasePath(String imageBasePath) { this.imageBasePath = imageBasePath; }
    public String getDefaultTransferYear() { return defaultTransferYear; }
    public void setDefaultTransferYear(String defaultTransferYear) { this.defaultTransferYear = defaultTransferYear; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getThreadCount() { return threadCount; }
    public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getFileTimeoutSeconds() { return fileTimeoutSeconds; }
    public void setFileTimeoutSeconds(int fileTimeoutSeconds) { this.fileTimeoutSeconds = fileTimeoutSeconds; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }

    public static class Llm {
        private List<String> endpoints = new ArrayList<>(List.of(
                "http://192.168.250.25:13006/v1/chat/completions",
                "http://192.168.250.25:14006/v1/chat/completions",
                "http://192.168.250.25:15006/v1/chat/completions",
                "http://192.168.250.25:16006/v1/chat/completions"
        ));
        private String model = "local-model";
        private int perWorkerSize = 10;
        private int timeoutSeconds = 120;
        private int maxContentLength = 12000;
        private int maxSummaryLength = 500;
        private String systemPrompt = "당신은 국가기록물 문서를 요약하는 전문 어시스턴트입니다. 제목과 본문을 보고 핵심 내용을 한국어로 간결하게 요약하세요.";

        public List<String> getEndpoints() { return endpoints; }
        public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getPerWorkerSize() { return perWorkerSize; }
        public void setPerWorkerSize(int perWorkerSize) { this.perWorkerSize = perWorkerSize; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxContentLength() { return maxContentLength; }
        public void setMaxContentLength(int maxContentLength) { this.maxContentLength = maxContentLength; }
        public int getMaxSummaryLength() { return maxSummaryLength; }
        public void setMaxSummaryLength(int maxSummaryLength) { this.maxSummaryLength = maxSummaryLength; }
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    }
}
