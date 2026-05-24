package com.saltlux.nac.elecdoc;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.extract.llm")
public class DocumentLlmProperties {

    private List<String> endpoints = new ArrayList<>();
    private String model = "local-model";
    private int perWorkerSize = 10;
    private int timeoutSeconds = 120;
    private int maxContentLength = 12000;
    private int maxSummaryLength = 500;
    private String systemPrompt = "당신은 국가기록물 문서를 요약하는 전문 어시스턴트입니다.";

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
