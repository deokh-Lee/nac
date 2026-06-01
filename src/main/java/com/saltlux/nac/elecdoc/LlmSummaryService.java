package com.saltlux.nac.elecdoc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saltlux.nac.prompt.PromptTemplate;
import com.saltlux.nac.prompt.PromptTemplateRenderer;
import com.saltlux.nac.prompt.PromptTemplateRepository;
import com.saltlux.nac.progress.LlmExtractProgressService;
import com.saltlux.nac.subject.SubjectPolicyCandidate;
import com.saltlux.nac.subject.SubjectPolicyMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmSummaryService {

    private static final Logger log = LoggerFactory.getLogger(LlmSummaryService.class);

    private final LlmSummaryMapper llmSummaryMapper;
    private final DocumentLlmProperties properties;
    private final PromptTemplateRepository promptTemplateRepository;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final SubjectPolicyMapper subjectPolicyMapper;
    private final LlmExtractProgressService progressService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmSummaryService(LlmSummaryMapper llmSummaryMapper,
                             DocumentLlmProperties properties,
                             PromptTemplateRepository promptTemplateRepository,
                             PromptTemplateRenderer promptTemplateRenderer,
                             SubjectPolicyMapper subjectPolicyMapper,
                             LlmExtractProgressService progressService,
                             RestTemplateBuilder restTemplateBuilder) {
        this.llmSummaryMapper = llmSummaryMapper;
        this.properties = properties;
        this.promptTemplateRepository = promptTemplateRepository;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.subjectPolicyMapper = subjectPolicyMapper;
        this.progressService = progressService;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    public LlmSummaryBatchResult summarizeBatch(String transferYear, Integer limit, Boolean retryFail) {
        return summarizeBatch(transferYear, limit, retryFail, null);
    }

    public LlmSummaryBatchResult summarizeBatch(String transferYear, Integer limit, Boolean retryFail, String promptName) {
        PromptTemplate promptTemplate = promptTemplateRepository.getOrDefault(promptName, properties.getDefaultPromptName());
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        int workerCount = Math.max(1, properties.getEndpoints().size());
        int perWorkerSize = Math.max(1, properties.getPerWorkerSize());
        int requestCount = limit == null || limit <= 0 ? workerCount * perWorkerSize : limit;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);

        List<LlmSummaryTarget> targets = llmSummaryMapper.findSummaryTargets(targetYear, requestCount, shouldRetryFail);
        if (targets.isEmpty()) {
            return new LlmSummaryBatchResult(targetYear, promptTemplate.name(), requestCount, 0, workerCount, perWorkerSize, 0, 0);
        }

        Long runId = null;
        WorkerResult result = new WorkerResult(0, 0);
        try {
            runId = progressService.startRun("SUMMARY_BATCH", targetYear, null, shouldRetryFail, requestCount, 1);
            result = summarizeTargets(promptTemplate, targets, workerCount, perWorkerSize, runId);
            progressService.finishRun(runId, true, 1, targets.size(), result.successCount(), result.failCount());
        } catch (RuntimeException e) {
            progressService.failRun(runId, 1, targets.size(), result.successCount(), result.failCount(), safeMessage(e));
            throw e;
        }

        return new LlmSummaryBatchResult(
                targetYear,
                promptTemplate.name(),
                requestCount,
                targets.size(),
                workerCount,
                perWorkerSize,
                result.successCount(),
                result.failCount()
        );
    }

    private WorkerResult summarizeTargets(PromptTemplate promptTemplate,
                                          List<LlmSummaryTarget> targets,
                                          int workerCount,
                                          int perWorkerSize,
                                          Long runId) {
        List<List<LlmSummaryTarget>> workerBuckets = distributeRoundRobin(targets, workerCount);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        List<Future<WorkerResult>> futures = new ArrayList<>();

        try {
            int submittedCount = 0;
            for (int i = 0; i < workerCount; i++) {
                List<LlmSummaryTarget> workerTargets = workerBuckets.get(i);
                if (workerTargets.isEmpty()) {
                    continue;
                }
                String endpoint = properties.getEndpoints().get(i);
                submittedCount += workerTargets.size();
                log.info("LLM worker assigned | workerNo={} | prompt={} | endpoint={} | size={}",
                        i + 1, promptTemplate.name(), endpoint, workerTargets.size());
                futures.add(executorService.submit(createWorkerTask(i + 1, endpoint, promptTemplate, workerTargets, runId)));
            }

            int success = 0;
            int fail = 0;
            for (Future<WorkerResult> future : futures) {
                try {
                    WorkerResult result = future.get();
                    success += result.successCount();
                    fail += result.failCount();
                } catch (Exception e) {
                    fail++;
                    log.warn("LLM summary worker future failed. error={}", safeMessage(e));
                }
            }

            return new WorkerResult(success, fail);
        } finally {
            executorService.shutdown();
        }
    }

    private List<List<LlmSummaryTarget>> distributeRoundRobin(List<LlmSummaryTarget> targets, int workerCount) {
        List<List<LlmSummaryTarget>> buckets = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            buckets.add(new ArrayList<>());
        }
        for (int i = 0; i < targets.size(); i++) {
            buckets.get(i % workerCount).add(targets.get(i));
        }
        return buckets;
    }

    public LlmSummaryAllBatchResult summarizeAll(String transferYear, Integer limit, Integer maxLoop, Boolean retryFail) {
        return summarizeAll(transferYear, limit, maxLoop, retryFail, null);
    }

    public LlmSummaryAllBatchResult summarizeAll(String transferYear, Integer limit, Integer maxLoop, Boolean retryFail, String promptName) {
        PromptTemplate promptTemplate = promptTemplateRepository.getOrDefault(promptName, properties.getDefaultPromptName());
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        int workerCount = Math.max(1, properties.getEndpoints().size());
        int perWorkerSize = Math.max(1, properties.getPerWorkerSize());
        int batchSize = limit == null || limit <= 0 ? workerCount * perWorkerSize : limit;
        int loopLimit = maxLoop == null || maxLoop <= 0 ? 10_000 : maxLoop;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);

        int loopCount = 0;
        int totalTargetCount = 0;
        int totalSuccessCount = 0;
        int totalFailCount = 0;
        boolean completed = false;
        Long runId = null;

        try {
            runId = progressService.startRun("SUMMARY_ALL", targetYear, null, shouldRetryFail, batchSize, loopLimit);
            while (loopCount < loopLimit) {
                loopCount++;
                List<LlmSummaryTarget> targets = llmSummaryMapper.findSummaryTargets(targetYear, batchSize, shouldRetryFail);
                WorkerResult result = targets.isEmpty()
                        ? new WorkerResult(0, 0)
                        : summarizeTargets(promptTemplate, targets, workerCount, perWorkerSize, runId);

                totalTargetCount += targets.size();
                totalSuccessCount += result.successCount();
                totalFailCount += result.failCount();

                log.info("llm-summary/all loop#{} | year={} | prompt={} | batchSize={} | target={} | success={} | fail={} | totalTarget={} | totalSuccess={} | totalFail={}",
                        loopCount,
                        targetYear,
                        promptTemplate.name(),
                        batchSize,
                        targets.size(),
                        result.successCount(),
                        result.failCount(),
                        totalTargetCount,
                        totalSuccessCount,
                        totalFailCount);

                progressService.updateRunProgress(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);

                if (targets.isEmpty()) {
                    completed = true;
                    break;
                }
            }
            progressService.finishRun(runId, completed, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);
        } catch (RuntimeException e) {
            progressService.failRun(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount, safeMessage(e));
            throw e;
        }

        return new LlmSummaryAllBatchResult(
                targetYear,
                promptTemplate.name(),
                batchSize,
                loopLimit,
                shouldRetryFail,
                loopCount,
                totalTargetCount,
                totalSuccessCount,
                totalFailCount,
                completed
        );
    }

    private Callable<WorkerResult> createWorkerTask(int workerNo,
                                                    String endpoint,
                                                    PromptTemplate promptTemplate,
                                                    List<LlmSummaryTarget> targets,
                                                    Long runId) {
        return () -> {
            int success = 0;
            int fail = 0;
            log.info("LLM worker start | workerNo={} | prompt={} | endpoint={} | size={}",
                    workerNo, promptTemplate.name(), endpoint, targets.size());

            for (LlmSummaryTarget target : targets) {
                try {
                    LlmSummaryResponse llmResult = callLlm(endpoint, promptTemplate, target);
                    llmSummaryMapper.updateSummarySuccess(
                            target,
                            llmResult.flag(),
                            cut(llmResult.summary(), properties.getMaxSummaryLength())
                    );
                    progressService.recordSummarySuccess(runId, target, llmResult.flag());
                    success++;
                    log.info("LLM summary PASS | workerNo={} | prompt={} | flag={} | fileName={} | zipSeq={} | rcRfileNo={} | rcRitemNo={}",
                            workerNo, promptTemplate.name(), llmResult.flag(), target.getFileName(), target.getZipSeq(), target.getRcRfileNo(), target.getRcRitemNo());
                } catch (Exception e) {
                    llmSummaryMapper.updateSummaryFail(target, cut(safeMessage(e), 2000));
                    progressService.recordSummaryFail(runId, target, safeMessage(e));
                    fail++;
                    log.warn("LLM summary FAIL | workerNo={} | prompt={} | fileName={} | zipSeq={} | rcRfileNo={} | rcRitemNo={} | error={}",
                            workerNo, promptTemplate.name(), target.getFileName(), target.getZipSeq(), target.getRcRfileNo(), target.getRcRitemNo(), safeMessage(e));
                }
            }

            log.info("LLM worker end | workerNo={} | prompt={} | success={} | fail={}",
                    workerNo, promptTemplate.name(), success, fail);
            return new WorkerResult(success, fail);
        };
    }

    private LlmSummaryResponse callLlm(String endpoint, PromptTemplate promptTemplate, LlmSummaryTarget target) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        String systemPrompt = promptTemplateRenderer.render(
                promptTemplate.content(),
                buildPromptVariables(target, promptTemplate.content())
        );

        Map<String, Object> payload = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", buildUserPrompt(target))
                ),
                "temperature", 0.2,
                "stream", false
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(payload, headers), Map.class);
        String content = parseContent(response.getBody());
        return parseSummaryJson(content);
    }

    private String buildUserPrompt(LlmSummaryTarget target) {
        StringBuilder sb = new StringBuilder();
        sb.append("[문서]\n");
        sb.append("BND_TTL: ").append(nullToEmpty(target.getBndTtl())).append("\n");
        sb.append("JEMOK: ").append(nullToEmpty(target.getJemok())).append("\n");
        sb.append("INDEXING_CONTENTS:\n");
        sb.append(cut(nullToEmpty(target.getIndexingContents()), properties.getMaxContentLength())).append("\n\n");
        sb.append("[출력 형식]\n");
        sb.append("반드시 아래 JSON 형식만 출력하세요. 설명 문장이나 코드블록은 출력하지 마세요.\n");
        sb.append("{\n");
        sb.append("  \"flag\": \"Y 또는 N\",\n");
        sb.append("  \"summary\": \"flag가 Y인 경우 300자 이내 요약, flag가 N인 경우 빈 문자열\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    private Map<String, Object> buildPromptVariables(LlmSummaryTarget target, String template) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("BND_TTL", nullToEmpty(target.getBndTtl()));
        variables.put("JEMOK", nullToEmpty(target.getJemok()));
        variables.put("RC_CODE", nullToEmpty(target.getRcCode()));
        variables.put("ALL_ORG_NM", nullToEmpty(target.getAllOrgNm()));
        variables.put("PRODREGDATE", nullToEmpty(target.getProdRegDate()));
        variables.put("PRODYEAR", nullToEmpty(target.getProdYear()));
        variables.put("RC_RFILE_NO", nullToEmpty(target.getRcRfileNo()));
        variables.put("RC_RITEM_NO", nullToEmpty(target.getRcRitemNo()));
        variables.put("ORG_FILE_PATH", nullToEmpty(target.getOrgFilePath()));
        variables.put("ORG_FILE_NAME", nullToEmpty(target.getOrgFileName()));
        variables.put("SAVE_FILE_NAME", nullToEmpty(target.getSaveFileName()));
        variables.put("FILE_NAME", nullToEmpty(target.getFileName()));
        variables.put("CONTENTS", cut(nullToEmpty(target.getContents()), properties.getMaxContentLength()));
        variables.put("INDEXING_CONTENTS", cut(nullToEmpty(target.getIndexingContents()), properties.getMaxContentLength()));
        variables.put("ZIP_SEQ", target.getZipSeq() == null ? "" : target.getZipSeq());
        variables.put("ZIP_ENTRY_FILE_NAME", nullToEmpty(target.getZipEntryFileName()));
        variables.put("FILE_TYPE", nullToEmpty(target.getFileType()));
        variables.put("FILE_GUBUN", nullToEmpty(target.getFileGubun()));
        variables.put("maxContentLength", properties.getMaxContentLength());
        variables.put("maxSummaryLength", properties.getMaxSummaryLength());
        if (containsPlaceholder(template, "candidate_policy_list_json")) {
            variables.put("candidate_policy_list_json", buildCandidatePolicyListJson(target));
        }
        return variables;
    }

    private boolean containsPlaceholder(String template, String name) {
        if (!StringUtils.hasText(template)) {
            return false;
        }
        return template.contains("${" + name + "}")
                || template.contains("{{" + name + "}}")
                || template.contains("{" + name + "}");
    }

    private String buildCandidatePolicyListJson(LlmSummaryTarget target) {
        String productionDate = StringUtils.hasText(target.getProdRegDate())
                ? normalizeProductionDate(target.getProdRegDate())
                : "";
        String productionYear = normalizeProductionYear(target.getProdYear());
        List<SubjectPolicyCandidate> candidates = subjectPolicyMapper.findSubjectCandidates("POLICY", productionDate, productionYear);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SubjectPolicyCandidate candidate : candidates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ITEM_CD", candidate.getItemCd());
            row.put("SUB_CLS_NM", candidate.getSubClsNm());
            row.put("TOPIC_NM", candidate.getTopicNm());
            row.put("PREV_GOV_NM", candidate.getPrevGovNm());
            row.put("TERN_START", candidate.getTernStart());
            row.put("TERN_END", candidate.getTernEnd());
            row.put("DESCRIPTION", candidate.getDescription());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize policy candidates", e);
        }
    }

    private String normalizeProductionDate(String productionDate) {
        if (!StringUtils.hasText(productionDate)) {
            return "";
        }
        String digits = productionDate.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            return digits.substring(0, 8);
        }
        if (digits.length() == 4) {
            return digits + "0101";
        }
        return productionDate;
    }

    private String normalizeProductionYear(String productionYear) {
        if (!StringUtils.hasText(productionYear)) {
            return "";
        }
        String digits = productionYear.replaceAll("[^0-9]", "");
        return digits.length() >= 4 ? digits.substring(0, 4) : productionYear;
    }

    @SuppressWarnings("unchecked")
    private String parseContent(Map body) {
        if (body == null) {
            throw new IllegalStateException("LLM response body is empty");
        }
        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("LLM response has no choices: " + body);
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            throw new IllegalStateException("Invalid LLM choice format: " + first);
        }
        Object messageObj = choice.get("message");
        if (messageObj instanceof Map<?, ?> message) {
            Object content = message.get("content");
            if (content != null) {
                return content.toString().trim();
            }
        }
        Object text = choice.get("text");
        if (text != null) {
            return text.toString().trim();
        }
        throw new IllegalStateException("LLM response has no content: " + body);
    }

    private LlmSummaryResponse parseSummaryJson(String content) throws Exception {
        String json = extractJson(content);
        JsonNode root = objectMapper.readTree(json);
        String flag = root.path("flag").asText("N").trim().toUpperCase();
        if (!"Y".equals(flag)) {
            flag = "N";
        }
        String summary = root.path("summary").asText("").trim();
        if ("N".equals(flag)) {
            summary = "";
        }
        return new LlmSummaryResponse(flag, summary);
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("LLM response content is empty");
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```") ) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("LLM response is not JSON: " + content);
        }
        return trimmed.substring(start, end + 1);
    }

    private String cut(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeMessage(Throwable e) {
        if (e == null) {
            return "";
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private record WorkerResult(int successCount, int failCount) {
    }

    private record LlmSummaryResponse(String flag, String summary) {
    }
}
