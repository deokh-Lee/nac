package com.saltlux.nac.elecdoc;

public class ExtractElecDoc {

    private String fileName;
    private String zipEntryFileName;
    private String rcRfileNo;
    private String rcRitemNo;
    private Integer zipSeq;
    private String contents;
    private String imgDatas;
    private String fileType;
    private String fileGubun;
    private String indexingContents;
    private String hwpSubTitle;
    private Integer dataYear;
    private String queueState;
    private String hasContents;
    private String implementOrg;
    private String implementDate;
    private String receiptOrg;
    private String receiptDate;
    private String extractStatus;
    private String extractErrMsg;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getZipEntryFileName() { return zipEntryFileName; }
    public void setZipEntryFileName(String zipEntryFileName) { this.zipEntryFileName = zipEntryFileName; }
    public String getRcRfileNo() { return rcRfileNo; }
    public void setRcRfileNo(String rcRfileNo) { this.rcRfileNo = rcRfileNo; }
    public String getRcRitemNo() { return rcRitemNo; }
    public void setRcRitemNo(String rcRitemNo) { this.rcRitemNo = rcRitemNo; }
    public Integer getZipSeq() { return zipSeq; }
    public void setZipSeq(Integer zipSeq) { this.zipSeq = zipSeq; }
    public String getContents() { return contents; }
    public void setContents(String contents) { this.contents = contents; }
    public String getImgDatas() { return imgDatas; }
    public void setImgDatas(String imgDatas) { this.imgDatas = imgDatas; }
    public String getFileType() { return fileType; }

    /**
     * EXTRACT_ELEC_DOC.FILE_TYPE에는 파일 확장자(HWP/PDF/ZIP)가 아니라
     * FILE_NAME 규칙의 문서 구분 값을 저장합니다.
     *
     * 예) 202311165978_000000000001_N01.hwp -> N, 시행문
     * 예) 202311167633_000000000004_S01.hwp -> S, 붙임문서
     */
    public void setFileType(String fileType) {
        String documentType = FileTypeUtils.documentTypeOf(this.fileName);
        this.fileType = "UNKNOWN".equals(documentType) ? fileType : documentType;
    }

    public String getFileGubun() { return fileGubun; }
    public void setFileGubun(String fileGubun) { this.fileGubun = fileGubun; }
    public String getIndexingContents() { return indexingContents; }
    public void setIndexingContents(String indexingContents) { this.indexingContents = indexingContents; }
    public String getHwpSubTitle() { return hwpSubTitle; }
    public void setHwpSubTitle(String hwpSubTitle) { this.hwpSubTitle = hwpSubTitle; }
    public Integer getDataYear() { return dataYear; }
    public void setDataYear(Integer dataYear) { this.dataYear = dataYear; }
    public String getQueueState() { return queueState; }
    public void setQueueState(String queueState) { this.queueState = queueState; }
    public String getHasContents() { return hasContents; }
    public void setHasContents(String hasContents) { this.hasContents = hasContents; }
    public String getImplementOrg() { return implementOrg; }
    public void setImplementOrg(String implementOrg) { this.implementOrg = implementOrg; }
    public String getImplementDate() { return implementDate; }
    public void setImplementDate(String implementDate) { this.implementDate = implementDate; }
    public String getReceiptOrg() { return receiptOrg; }
    public void setReceiptOrg(String receiptOrg) { this.receiptOrg = receiptOrg; }
    public String getReceiptDate() { return receiptDate; }
    public void setReceiptDate(String receiptDate) { this.receiptDate = receiptDate; }
    public String getExtractStatus() { return extractStatus; }
    public void setExtractStatus(String extractStatus) { this.extractStatus = extractStatus; }
    public String getExtractErrMsg() { return extractErrMsg; }
    public void setExtractErrMsg(String extractErrMsg) { this.extractErrMsg = extractErrMsg; }
}
