package com.saltlux.nac.progress;

public class LlmExtractItemLog {
    private Long runId;
    private String subjectType;
    private String rcCode;
    private String rcRfileNo;
    private String rcRitemNo;
    private String status;
    private String itemCd;
    private String itemNm;
    private String errorMsg;

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getRcCode() { return rcCode; }
    public void setRcCode(String rcCode) { this.rcCode = rcCode; }
    public String getRcRfileNo() { return rcRfileNo; }
    public void setRcRfileNo(String rcRfileNo) { this.rcRfileNo = rcRfileNo; }
    public String getRcRitemNo() { return rcRitemNo; }
    public void setRcRitemNo(String rcRitemNo) { this.rcRitemNo = rcRitemNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getItemCd() { return itemCd; }
    public void setItemCd(String itemCd) { this.itemCd = itemCd; }
    public String getItemNm() { return itemNm; }
    public void setItemNm(String itemNm) { this.itemNm = itemNm; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}
