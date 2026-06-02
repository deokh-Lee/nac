package com.saltlux.nac.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saltlux.nac.elecdoc.DocumentLlmProperties;
import com.saltlux.nac.prompt.PromptTemplate;
import com.saltlux.nac.prompt.PromptTemplateRenderer;
import com.saltlux.nac.prompt.PromptTemplateRepository;
import com.saltlux.nac.progress.LlmExtractProgressService;
import com.saltlux.nac.subject.SubjectExtractRunRegistry;
import com.saltlux.nac.subject.SubjectPolicyCandidate;
import com.saltlux.nac.subject.SubjectPolicyMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
public class PolicyExtractService {

    private static final Logger log = LoggerFactory.getLogger(PolicyExtractService.class);
    private static final String PROMPT_NAME = "policy_extract";
    private static final List<String> SAMPLE_DEPARTMENTS = List.of("환경부", "복지부", "법제처");

    private final PolicyExtractMapper policyExtractMapper;
    private final SubjectPolicyMapper subjectPolicyMapper;
    private final DocumentLlmProperties properties;
    private final PromptTemplateRepository promptTemplateRepository;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final SubjectExtractRunRegistry runRegistry;
    private final LlmExtractProgressService progressService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<CandidateCacheKey, String> candidateListJsonCache = new ConcurrentHashMap<>();

    public PolicyExtractService(PolicyExtractMapper policyExtractMapper,
                                SubjectPolicyMapper subjectPolicyMapper,
                                DocumentLlmProperties properties,
                                PromptTemplateRepository promptTemplateRepository,
                                PromptTemplateRenderer promptTemplateRenderer,
                                SubjectExtractRunRegistry runRegistry,
                                LlmExtractProgressService progressService,
                                RestTemplateBuilder restTemplateBuilder) {
        this.policyExtractMapper = policyExtractMapper;
        this.subjectPolicyMapper = subjectPolicyMapper;
        this.properties = properties;
        this.promptTemplateRepository = promptTemplateRepository;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.runRegistry = runRegistry;
        this.progressService = progressService;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    public PolicyExtractResult extractBatch(String transferYear, String prodYear, Integer limit, Integer offset, Boolean retryFail) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = normalizeProductionYear(prodYear);
        int requestCount = limit == null || limit <= 0 ? 100 : limit;
        int requestOffset = offset == null || offset < 0 ? 0 : offset;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();

        List<PolicyExtractTarget> targets = policyExtractMapper.findPolicyExtractTargets(
                targetYear,
                targetProdYear,
                requestCount,
                requestOffset,
                shouldRetryFail
        );
        if (targets.isEmpty()) {
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, shouldRetryFail, 0, workerCount, 0, 0);
        }

        PromptTemplate promptTemplate = promptTemplateRepository.get(PROMPT_NAME);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;
        int successCount = 0;
        int failCount = 0;
        try {
            runId = progressService.startRun("POLICY_BATCH", targetYear, targetProdYear, shouldRetryFail, requestCount, 1);
            WorkerResult result = runWorkerBatch(executorService, endpoints, promptTemplate, targets, runId);
            successCount = result.successCount();
            failCount = result.failCount();
            progressService.finishRun(runId, true, 1, targets.size(), successCount, failCount);
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, shouldRetryFail,
                    targets.size(), workerCount, successCount, failCount);
        } catch (RuntimeException e) {
            progressService.failRun(runId, 1, targets.size(), successCount, failCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
        }
    }

    public PolicyExtractAllResult extractAll(String transferYear, String prodYear, Integer limit, Integer maxLoop, Boolean retryFail) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = normalizeProductionYear(prodYear);
        int batchSize = limit == null || limit <= 0 ? 100 : limit;
        int loopLimit = maxLoop == null || maxLoop <= 0 ? 10_000 : maxLoop;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);
        SubjectExtractRunRegistry.RunKey runKey = runRegistry.acquire(
                "policy-extract/all",
                SubjectExtractRunRegistry.POLICY,
                targetYear,
                targetProdYear
        );
        int loopCount = 0;
        int totalTargetCount = 0;
        int totalSuccessCount = 0;
        int totalFailCount = 0;
        boolean completed = false;
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();
        PromptTemplate promptTemplate = promptTemplateRepository.get(PROMPT_NAME);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;

        try {
            runId = progressService.startRun("POLICY_ALL", targetYear, targetProdYear, shouldRetryFail, batchSize, loopLimit);
            while (loopCount < loopLimit) {
                loopCount++;
                List<PolicyExtractTarget> targets = policyExtractMapper.findPolicyExtractTargets(
                        targetYear,
                        targetProdYear,
                        batchSize,
                        0,
                        shouldRetryFail
                );
                WorkerResult result = targets.isEmpty()
                        ? new WorkerResult(0, 0)
                        : runWorkerBatch(executorService, endpoints, promptTemplate, targets, runId);
                totalTargetCount += targets.size();
                totalSuccessCount += result.successCount();
                totalFailCount += result.failCount();

                log.info("policy-extract/all loop#{} | year={} | prodYear={} | batchSize={} | retryFail={} | target={} | success={} | fail={} | totalTarget={} | totalSuccess={} | totalFail={}",
                        loopCount,
                        targetYear,
                        targetProdYear,
                        batchSize,
                        shouldRetryFail,
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
            return new PolicyExtractAllResult(
                    targetYear,
                    targetProdYear,
                    batchSize,
                    loopLimit,
                    shouldRetryFail,
                    loopCount,
                    totalTargetCount,
                    totalSuccessCount,
                    totalFailCount,
                    completed
            );
        } catch (RuntimeException e) {
            progressService.failRun(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
            runRegistry.release(runKey);
        }
    }

    public PolicyExtractResult extractSample(String transferYear, String prodYear, Integer limit, Integer offset, Boolean retryFail) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int requestCount = limit == null || limit <= 0 ? 100 : limit;
        int requestOffset = offset == null || offset < 0 ? 0 : offset;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();

        List<PolicyExtractTarget> targets = policyExtractMapper.findPolicyExtractSampleTargets(
                targetYear,
                targetProdYear,
                SAMPLE_DEPARTMENTS,
                requestCount,
                requestOffset,
                shouldRetryFail
        );
        if (targets.isEmpty()) {
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, shouldRetryFail, 0, workerCount, 0, 0);
        }

        PromptTemplate promptTemplate = promptTemplateRepository.get(properties.getPolicyTestPromptName());
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;
        int successCount = 0;
        int failCount = 0;
        try {
            runId = progressService.startRun("POLICY_SAMPLE", targetYear, targetProdYear, shouldRetryFail, requestCount, 1);
            WorkerResult result = runWorkerBatch(executorService, endpoints, promptTemplate, targets, runId);
            successCount = result.successCount();
            failCount = result.failCount();
            progressService.finishRun(runId, true, 1, targets.size(), successCount, failCount);
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, shouldRetryFail,
                    targets.size(), workerCount, successCount, failCount);
        } catch (RuntimeException e) {
            progressService.failRun(runId, 1, targets.size(), successCount, failCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
        }
    }

    private WorkerResult runWorkerBatch(ExecutorService executorService,
                                        List<String> endpoints,
                                        PromptTemplate promptTemplate,
                                        List<PolicyExtractTarget> targets,
                                        Long runId) {
        int workerCount = endpoints.size();
        List<List<PolicyExtractTarget>> workerBuckets = distributeRoundRobin(targets, workerCount);
        List<Future<WorkerResult>> futures = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            List<PolicyExtractTarget> workerTargets = workerBuckets.get(i);
            if (workerTargets.isEmpty()) {
                continue;
            }
            String endpoint = endpoints.get(i);
            log.info("Policy extract worker assigned | workerNo={} | endpoint={} | size={}",
                    i + 1, endpoint, workerTargets.size());
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
                log.warn("Policy extract worker future failed. error={}", safeMessage(e));
            }
        }
        return new WorkerResult(success, fail);
    }

    private Callable<WorkerResult> createWorkerTask(int workerNo,
                                                    String endpoint,
                                                    PromptTemplate promptTemplate,
                                                    List<PolicyExtractTarget> targets,
                                                    Long runId) {
        return () -> {
            int success = 0;
            int fail = 0;
            log.info("Policy extract worker start | workerNo={} | endpoint={} | size={}", workerNo, endpoint, targets.size());

            for (PolicyExtractTarget target : targets) {
                try {
                    PolicyExtractResponse result = callLlm(endpoint, promptTemplate, target);
                    policyExtractMapper.updatePolicyExtractSuccess(target, result);
                    progressService.recordSuccess(runId, "POLICY", target, result.itemCd(), result.policy());
                    success++;
                    log.info("Policy extract record result | status=PASS | workerNo={} | rcCode={} | rcRfileNo={} | rcRitemNo={} | itemCd={} | value={} | reason={}",
                            workerNo, target.getRcCode(), target.getRcRfileNo(), target.getRcRitemNo(),
                            result.itemCd(), cut(result.policy(), 100), cut(result.reason(), 200));
                } catch (Exception e) {
                    policyExtractMapper.updatePolicyExtractFail(target, cut(safeMessage(e), 100));
                    progressService.recordFail(runId, "POLICY", target, safeMessage(e));
                    fail++;
                    log.warn("Policy extract record result | status=FAIL | workerNo={} | rcCode={} | rcRfileNo={} | rcRitemNo={} | error={}",
                            workerNo, target.getRcCode(), target.getRcRfileNo(), target.getRcRitemNo(), cut(safeMessage(e), 4000));
                }
            }

            log.info("Policy extract worker end | workerNo={} | success={} | fail={}", workerNo, success, fail);
            return new WorkerResult(success, fail);
        };
    }

    private PolicyExtractResponse callLlm(String endpoint, PromptTemplate promptTemplate, PolicyExtractTarget target) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        String prompt = promptTemplateRenderer.render(promptTemplate.content(), buildPromptVariables(target));

        Map<String, Object> payload = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "반드시 JSON 객체만 출력하세요."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2,
                "stream", false
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(payload, headers), Map.class);
        String content = parseContent(response.getBody());
        return parsePolicyExtractJson(content, target);
    }

    private Map<String, Object> buildPromptVariables(PolicyExtractTarget target) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("RC_CODE", nullToEmpty(target.getRcCode()));
        variables.put("RC_RFILE_NO", nullToEmpty(target.getRcRfileNo()));
        variables.put("RC_RITEM_NO", nullToEmpty(target.getRcRitemNo()));
        variables.put("CLSS_ID", nullToEmpty(target.getClssId()));
        variables.put("BND_TTL", nullToEmpty(target.getBndTtl()));
        variables.put("JEMOK", nullToEmpty(target.getJemok()));
        variables.put("ALL_ORG_NM", nullToEmpty(target.getAllOrgNm()));
        variables.put("DEPT_NM", nullToEmpty(target.getDeptNm()));
        variables.put("PRODREGDATE", nullToEmpty(target.getProdRegDate()));
        variables.put("PRODYEAR", nullToEmpty(target.getProdYear()));
        variables.put("candidate_policy_list_json", buildCandidatePolicyListJson(target));
        return variables;
    }

    private String buildCandidatePolicyListJson(PolicyExtractTarget target) {
        String productionDate = StringUtils.hasText(target.getProdRegDate())
                ? normalizeProductionDate(target.getProdRegDate())
                : "";
        String productionYear = normalizeProductionYear(target.getProdYear());
        CandidateCacheKey cacheKey = new CandidateCacheKey("POLICY", productionDate, productionYear);
        return candidateListJsonCache.computeIfAbsent(cacheKey, this::loadCandidateListJson);
    }

    private String loadCandidateListJson(CandidateCacheKey cacheKey) {
        List<SubjectPolicyCandidate> candidates = subjectPolicyMapper.findSubjectCandidates(
                cacheKey.clsCd(),
                cacheKey.productionDate(),
                cacheKey.productionYear()
        );
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
            throw new IllegalStateException("Failed to serialize " + cacheKey.clsCd() + " candidates", e);
        }
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

    private PolicyExtractResponse parsePolicyExtractJson(String content, PolicyExtractTarget target) throws Exception {
        JsonNode root = parseLlmJson("POLICY", content);
        return new PolicyExtractResponse(
                textOrDefault(root, "RC_CODE", target.getRcCode()),
                textOrDefault(root, "RC_RFILE_NO", target.getRcRfileNo()),
                textOrDefault(root, "RC_RITEM_NO", target.getRcRitemNo()),
                textOrDefault(root, "BND_TTL", target.getBndTtl()),
                textOrDefault(root, "JEMOK", target.getJemok()),
                textOrDefault(root, "POLICY", ""),
                textOrDefault(root, "ITEM_CD", ""),
                textOrDefault(root, "REASON", "")
        );
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("LLM response content is empty");
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("LLM response is not JSON: " + content);
        }
        return trimmed.substring(start, end + 1);
    }

    private JsonNode parseLlmJson(String subjectType, String content) throws Exception {
        try {
            return objectMapper.readTree(extractJson(content));
        } catch (Exception e) {
            throw new IllegalStateException("LLM " + subjectType + " response JSON parse failed | error="
                    + safeMessage(e) + " | response=" + cut(content, 4000), e);
        }
    }

    private String textOrDefault(JsonNode root, String fieldName, String defaultValue) {
        String value = root.path(fieldName).asText("").trim();
        return StringUtils.hasText(value) ? value : nullToEmpty(defaultValue);
    }

    private List<List<PolicyExtractTarget>> distributeRoundRobin(List<PolicyExtractTarget> targets, int workerCount) {
        List<List<PolicyExtractTarget>> buckets = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            buckets.add(new ArrayList<>());
        }
        for (int i = 0; i < targets.size(); i++) {
            buckets.get(i % workerCount).add(targets.get(i));
        }
        return buckets;
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

    private String resolveEndpoint(String configuredEndpoint, int fallbackIndex) {
        if (StringUtils.hasText(configuredEndpoint)) {
            return configuredEndpoint;
        }
        List<String> endpoints = properties.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalStateException("LLM endpoint is not configured");
        }
        int index = Math.min(Math.max(fallbackIndex, 0), endpoints.size() - 1);
        return endpoints.get(index);
    }

    private List<String> resolvePolicyEndpoints() {
        if (!properties.isPolicyUseEndpoints()) {
            return List.of(resolveEndpoint(properties.getPolicyEndpoint(), 0));
        }

        List<String> configuredEndpoints = properties.getEndpoints();
        List<String> endpoints = new ArrayList<>();
        if (configuredEndpoints != null) {
            for (String endpoint : configuredEndpoints) {
                if (StringUtils.hasText(endpoint)) {
                    endpoints.add(endpoint);
                }
            }
        }
        if (!endpoints.isEmpty()) {
            return endpoints;
        }
        return List.of(resolveEndpoint(properties.getPolicyEndpoint(), 0));
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

    private record CandidateCacheKey(String clsCd, String productionDate, String productionYear) {
    }
}
