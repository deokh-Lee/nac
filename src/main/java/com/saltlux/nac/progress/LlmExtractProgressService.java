package com.saltlux.nac.progress;

import com.saltlux.nac.policy.PolicyExtractTarget;
import com.saltlux.nac.elecdoc.ExtractElecDoc;
import com.saltlux.nac.elecdoc.LlmSummaryTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmExtractProgressService {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String ITEM_STATUS_PASS = "PASS";
    public static final String ITEM_STATUS_FAIL = "FAIL";

    private static final Logger log = LoggerFactory.getLogger(LlmExtractProgressService.class);

    private final LlmExtractProgressMapper progressMapper;

    public LlmExtractProgressService(LlmExtractProgressMapper progressMapper) {
        this.progressMapper = progressMapper;
    }

    public Long startRun(String processType,
                         String transferYear,
                         String prodYear,
                         boolean retryFail,
                         int batchSize,
                         int maxLoop) {
        LlmExtractRun run = new LlmExtractRun();
        run.setProcessType(processType);
        run.setTransferYear(transferYear);
        run.setProdYear(emptyToNull(prodYear));
        run.setRetryFail(retryFail ? "Y" : "N");
        run.setBatchSize(batchSize);
        run.setMaxLoop(maxLoop);
        run.setStatus(STATUS_RUNNING);
        progressMapper.insertRun(run);
        return run.getRunId();
    }

    public void finishRun(Long runId,
                          boolean completed,
                          int loopCount,
                          int totalTargetCount,
                          int totalSuccessCount,
                          int totalFailCount) {
        String status = completed && totalFailCount == 0 ? STATUS_SUCCESS : STATUS_PARTIAL;
        finishRun(runId, status, loopCount, totalTargetCount, totalSuccessCount, totalFailCount, null);
    }

    public void failRun(Long runId,
                        int loopCount,
                        int totalTargetCount,
                        int totalSuccessCount,
                        int totalFailCount,
                        String errorMsg) {
        finishRun(runId, STATUS_FAIL, loopCount, totalTargetCount, totalSuccessCount, totalFailCount, errorMsg);
    }

    public void updateRunProgress(Long runId,
                                  int loopCount,
                                  int totalTargetCount,
                                  int totalSuccessCount,
                                  int totalFailCount) {
        if (runId == null) {
            return;
        }
        try {
            progressMapper.updateRunProgress(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);
        } catch (Exception e) {
            log.warn("Failed to update LLM extract run progress | runId={} | loopCount={} | error={}",
                    runId, loopCount, safeMessage(e));
        }
    }

    public void recordSuccess(Long runId,
                              String subjectType,
                              PolicyExtractTarget target,
                              String itemCd,
                              String itemNm) {
        insertItemLog(runId, subjectType, target, ITEM_STATUS_PASS, itemCd, itemNm, null);
    }

    public void recordFail(Long runId,
                           String subjectType,
                           PolicyExtractTarget target,
                           String errorMsg) {
        insertItemLog(runId, subjectType, target, ITEM_STATUS_FAIL, null, null, errorMsg);
    }

    public void recordTextResult(Long runId, ExtractElecDoc extract) {
        if (extract == null) {
            return;
        }
        String status = ITEM_STATUS_PASS.equals(extract.getExtractStatus()) ? ITEM_STATUS_PASS : ITEM_STATUS_FAIL;
        String itemName = extract.getFileName();
        if (extract.getZipEntryFileName() != null && !extract.getZipEntryFileName().isBlank()) {
            itemName = extract.getFileName() + " / " + extract.getZipEntryFileName();
        }
        insertItemLog(
                runId,
                "TEXT",
                null,
                extract.getRcRfileNo(),
                extract.getRcRitemNo(),
                status,
                extract.getFileGubun(),
                itemName,
                extract.getExtractErrMsg()
        );
    }

    public void recordSummarySuccess(Long runId, LlmSummaryTarget target, String flag) {
        insertItemLog(
                runId,
                "SUMMARY",
                target.getRcCode(),
                target.getRcRfileNo(),
                target.getRcRitemNo(),
                ITEM_STATUS_PASS,
                flag,
                summaryItemName(target),
                null
        );
    }

    public void recordSummaryFail(Long runId, LlmSummaryTarget target, String errorMsg) {
        insertItemLog(
                runId,
                "SUMMARY",
                target.getRcCode(),
                target.getRcRfileNo(),
                target.getRcRitemNo(),
                ITEM_STATUS_FAIL,
                null,
                summaryItemName(target),
                errorMsg
        );
    }

    private void finishRun(Long runId,
                           String status,
                           int loopCount,
                           int totalTargetCount,
                           int totalSuccessCount,
                           int totalFailCount,
                           String errorMsg) {
        if (runId == null) {
            return;
        }
        progressMapper.finishRun(runId, status, loopCount, totalTargetCount, totalSuccessCount, totalFailCount, cut(errorMsg, 4000));
    }

    private void insertItemLog(Long runId,
                               String subjectType,
                               PolicyExtractTarget target,
                               String status,
                               String itemCd,
                               String itemNm,
                               String errorMsg) {
        insertItemLog(
                runId,
                subjectType,
                target.getRcCode(),
                target.getRcRfileNo(),
                target.getRcRitemNo(),
                status,
                itemCd,
                itemNm,
                errorMsg
        );
    }

    private void insertItemLog(Long runId,
                               String subjectType,
                               String rcCode,
                               String rcRfileNo,
                               String rcRitemNo,
                               String status,
                               String itemCd,
                               String itemNm,
                               String errorMsg) {
        if (runId == null) {
            return;
        }
        try {
            LlmExtractItemLog itemLog = new LlmExtractItemLog();
            itemLog.setRunId(runId);
            itemLog.setSubjectType(subjectType);
            itemLog.setRcCode(rcCode);
            itemLog.setRcRfileNo(rcRfileNo);
            itemLog.setRcRitemNo(rcRitemNo);
            itemLog.setStatus(status);
            itemLog.setItemCd(cut(itemCd, 100));
            itemLog.setItemNm(cut(itemNm, 500));
            itemLog.setErrorMsg(cut(errorMsg, 4000));
            progressMapper.insertItemLog(itemLog);
        } catch (Exception e) {
            log.warn("Failed to save LLM extract item log | runId={} | subjectType={} | status={} | error={}",
                    runId, subjectType, status, safeMessage(e));
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String summaryItemName(LlmSummaryTarget target) {
        String itemName = target.getFileName();
        if (target.getZipEntryFileName() != null && !target.getZipEntryFileName().isBlank()) {
            itemName = target.getFileName() + " / " + target.getZipEntryFileName();
        }
        return itemName;
    }

    private String cut(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safeMessage(Throwable e) {
        if (e == null) {
            return "";
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
