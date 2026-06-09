package com.saltlux.nac.chunk;

public class ChunkSourceDocument {

    private Long extractIdx;
    private String fileName;
    private String zipEntryFileName;
    private String rcRfileNo;
    private String rcRitemNo;
    private Integer zipSeq;
    private String fileType;
    private String fileGubun;
    private String indexingContents;
    private String hwpSubTitle;
    private Integer dataYear;

    public Long getExtractIdx() {
        return extractIdx;
    }

    public void setExtractIdx(Long extractIdx) {
        this.extractIdx = extractIdx;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getZipEntryFileName() {
        return zipEntryFileName;
    }

    public void setZipEntryFileName(String zipEntryFileName) {
        this.zipEntryFileName = zipEntryFileName;
    }

    public String getRcRfileNo() {
        return rcRfileNo;
    }

    public void setRcRfileNo(String rcRfileNo) {
        this.rcRfileNo = rcRfileNo;
    }

    public String getRcRitemNo() {
        return rcRitemNo;
    }

    public void setRcRitemNo(String rcRitemNo) {
        this.rcRitemNo = rcRitemNo;
    }

    public Integer getZipSeq() {
        return zipSeq;
    }

    public void setZipSeq(Integer zipSeq) {
        this.zipSeq = zipSeq;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileGubun() {
        return fileGubun;
    }

    public void setFileGubun(String fileGubun) {
        this.fileGubun = fileGubun;
    }

    public String getIndexingContents() {
        return indexingContents;
    }

    public void setIndexingContents(String indexingContents) {
        this.indexingContents = indexingContents;
    }

    public String getHwpSubTitle() {
        return hwpSubTitle;
    }

    public void setHwpSubTitle(String hwpSubTitle) {
        this.hwpSubTitle = hwpSubTitle;
    }

    public Integer getDataYear() {
        return dataYear;
    }

    public void setDataYear(Integer dataYear) {
        this.dataYear = dataYear;
    }
}
