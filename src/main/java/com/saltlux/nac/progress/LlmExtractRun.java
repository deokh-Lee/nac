package com.saltlux.nac.progress;

public class LlmExtractRun {
    private Long runId;
    private String processType;
    private String transferYear;
    private String prodYear;
    private String retryFail;
    private Integer batchSize;
    private Integer maxLoop;
    private String status;

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getProcessType() { return processType; }
    public void setProcessType(String processType) { this.processType = processType; }
    public String getTransferYear() { return transferYear; }
    public void setTransferYear(String transferYear) { this.transferYear = transferYear; }
    public String getProdYear() { return prodYear; }
    public void setProdYear(String prodYear) { this.prodYear = prodYear; }
    public String getRetryFail() { return retryFail; }
    public void setRetryFail(String retryFail) { this.retryFail = retryFail; }
    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }
    public Integer getMaxLoop() { return maxLoop; }
    public void setMaxLoop(Integer maxLoop) { this.maxLoop = maxLoop; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
