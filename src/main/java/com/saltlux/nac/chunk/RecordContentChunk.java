package com.saltlux.nac.chunk;

public class RecordContentChunk {

    private Long chunkId;
    private Long extractIdx;
    private String rcRfileNo;
    private String rcRitemNo;
    private String fileName;
    private String zipEntryFileName;
    private Integer zipSeq;
    private Integer chunkSeq;
    private String docRole;
    private String docType;
    private String sectionPath;
    private String lawChapter;
    private String lawSection;
    private String lawArticleNo;
    private String lawArticleTitle;
    private String lawArticleContent;
    private String chunkText;
    private Integer startOffset;
    private Integer endOffset;
    private Integer charCount;
    private Integer tokenCount;
    private String chunkStatus;

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getExtractIdx() {
        return extractIdx;
    }

    public void setExtractIdx(Long extractIdx) {
        this.extractIdx = extractIdx;
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

    public Integer getZipSeq() {
        return zipSeq;
    }

    public void setZipSeq(Integer zipSeq) {
        this.zipSeq = zipSeq;
    }

    public Integer getChunkSeq() {
        return chunkSeq;
    }

    public void setChunkSeq(Integer chunkSeq) {
        this.chunkSeq = chunkSeq;
    }

    public String getDocRole() {
        return docRole;
    }

    public void setDocRole(String docRole) {
        this.docRole = docRole;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getSectionPath() {
        return sectionPath;
    }

    public void setSectionPath(String sectionPath) {
        this.sectionPath = sectionPath;
    }

    public String getLawChapter() {
        return lawChapter;
    }

    public void setLawChapter(String lawChapter) {
        this.lawChapter = lawChapter;
    }

    public String getLawSection() {
        return lawSection;
    }

    public void setLawSection(String lawSection) {
        this.lawSection = lawSection;
    }

    public String getLawArticleNo() {
        return lawArticleNo;
    }

    public void setLawArticleNo(String lawArticleNo) {
        this.lawArticleNo = lawArticleNo;
    }

    public String getLawArticleTitle() {
        return lawArticleTitle;
    }

    public void setLawArticleTitle(String lawArticleTitle) {
        this.lawArticleTitle = lawArticleTitle;
    }

    public String getLawArticleContent() {
        return lawArticleContent;
    }

    public void setLawArticleContent(String lawArticleContent) {
        this.lawArticleContent = lawArticleContent;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public Integer getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(Integer startOffset) {
        this.startOffset = startOffset;
    }

    public Integer getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(Integer endOffset) {
        this.endOffset = endOffset;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public void setCharCount(Integer charCount) {
        this.charCount = charCount;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getChunkStatus() {
        return chunkStatus;
    }

    public void setChunkStatus(String chunkStatus) {
        this.chunkStatus = chunkStatus;
    }
}
