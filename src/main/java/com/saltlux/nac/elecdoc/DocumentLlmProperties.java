package com.saltlux.nac.elecdoc;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.extract.llm")
public class DocumentLlmProperties {

    private List<String> endpoints = new ArrayList<>();
    private boolean policyUseEndpoints = false;
    private String policyEndpoint;
    private String eventEndpoint;
    private String activityEndpoint;
    private String model = "local-model";
    private String defaultPromptName = "summary";
    private int perWorkerSize = 10;
    private int timeoutSeconds = 120;
    private int maxContentLength = 12000;
    private int maxSummaryLength = 500;

    public List<String> getEndpoints() { return endpoints; }
    public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }
    public boolean isPolicyUseEndpoints() { return policyUseEndpoints; }
    public void setPolicyUseEndpoints(boolean policyUseEndpoints) { this.policyUseEndpoints = policyUseEndpoints; }
    public String getPolicyEndpoint() { return policyEndpoint; }
    public void setPolicyEndpoint(String policyEndpoint) { this.policyEndpoint = policyEndpoint; }
    public String getEventEndpoint() { return eventEndpoint; }
    public void setEventEndpoint(String eventEndpoint) { this.eventEndpoint = eventEndpoint; }
    public String getActivityEndpoint() { return activityEndpoint; }
    public void setActivityEndpoint(String activityEndpoint) { this.activityEndpoint = activityEndpoint; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getDefaultPromptName() { return defaultPromptName; }
    public void setDefaultPromptName(String defaultPromptName) { this.defaultPromptName = defaultPromptName; }
    public int getPerWorkerSize() { return perWorkerSize; }
    public void setPerWorkerSize(int perWorkerSize) { this.perWorkerSize = perWorkerSize; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxContentLength() { return maxContentLength; }
    public void setMaxContentLength(int maxContentLength) { this.maxContentLength = maxContentLength; }
    public int getMaxSummaryLength() { return maxSummaryLength; }
    public void setMaxSummaryLength(int maxSummaryLength) { this.maxSummaryLength = maxSummaryLength; }
}
