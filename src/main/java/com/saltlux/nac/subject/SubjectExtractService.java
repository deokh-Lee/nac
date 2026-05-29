package com.saltlux.nac.subject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saltlux.nac.activity.ActivityExtractMapper;
import com.saltlux.nac.activity.ActivityExtractResponse;
import com.saltlux.nac.elecdoc.DocumentLlmProperties;
import com.saltlux.nac.event.EventExtractMapper;
import com.saltlux.nac.event.EventExtractResponse;
import com.saltlux.nac.policy.PolicyExtractMapper;
import com.saltlux.nac.policy.PolicyExtractResponse;
import com.saltlux.nac.policy.PolicyExtractTarget;
import com.saltlux.nac.prompt.PromptTemplate;
import com.saltlux.nac.prompt.PromptTemplateRenderer;
import com.saltlux.nac.prompt.PromptTemplateRepository;
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
public class SubjectExtractService {

    private static final Logger log = LoggerFactory.getLogger(SubjectExtractService.class);
    private static final String STATUS_PASS = "PASS";

    private final SubjectExtractMapper subjectExtractMapper;
    private final SubjectPolicyMapper subjectPolicyMapper;
    private final PolicyExtractMapper policyExtractMapper;
    private final EventExtractMapper eventExtractMapper;
    private final ActivityExtractMapper activityExtractMapper;
    private final DocumentLlmProperties properties;
    private final PromptTemplateRepository promptTemplateRepository;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubjectExtractService(SubjectExtractMapper subjectExtractMapper,
                                 SubjectPolicyMapper subjectPolicyMapper,
                                 PolicyExtractMapper policyExtractMapper,
                                 EventExtractMapper eventExtractMapper,
                                 ActivityExtractMapper activityExtractMapper,
                                 DocumentLlmProperties properties,
                                 PromptTemplateRepository promptTemplateRepository,
                                 PromptTemplateRenderer promptTemplateRenderer,
                                 RestTemplateBuilder restTemplateBuilder) {
        this.subjectExtractMapper = subjectExtractMapper;
        this.subjectPolicyMapper = subjectPolicyMapper;
        this.policyExtractMapper = policyExtractMapper;
        this.eventExtractMapper = eventExtractMapper;
        this.activityExtractMapper = activityExtractMapper;
        this.properties = properties;
        this.promptTemplateRepository = promptTemplateRepository;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    public SubjectExtractResult extractBatch(String transferYear, String prodYear, Integer limit, Integer offset, Boolean retryFail) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = normalizeProductionYear(prodYear);
        int requestCount = limit == null || limit <= 0 ? 100 : limit;
        int requestOffset = offset == null || offset < 0 ? 0 : offset;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);
        int workerCount = 3;

        List<PolicyExtractTarget> targets = subjectExtractMapper.findSubjectExtractTargets(
                targetYear,
                targetProdYear,
                requestCount,
                requestOffset,
                shouldRetryFail
        );

        String policyEndpoint = resolveEndpoint(properties.getPolicyEndpoint(), 0);
        String eventEndpoint = resolveEndpoint(properties.getEventEndpoint(), 1);
        String activityEndpoint = resolveEndpoint(properties.getActivityEndpoint(), 2);

        if (targets.isEmpty()) {
            return new SubjectExtractResult(
                    targetYear,
                    targetProdYear,
                    requestCount,
                    requestOffset,
                    shouldRetryFail,
                    0,
                    workerCount,
                    emptyResult("POLICY", policyEndpoint),
                    emptyResult("EVENT", eventEndpoint),
                    emptyResult("ACTIVITY", activityEndpoint)
            );
        }

        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        List<Future<SubjectExtractItemResult>> futures = new ArrayList<>();
        try {
            futures.add(executorService.submit(createPolicyTask(policyEndpoint, targets, shouldRetryFail)));
            futures.add(executorService.submit(createEventTask(eventEndpoint, targets, shouldRetryFail)));
            futures.add(executorService.submit(createActivityTask(activityEndpoint, targets, shouldRetryFail)));

            SubjectExtractItemResult policy = getFutureResult(futures.get(0), "POLICY", policyEndpoint);
            SubjectExtractItemResult event = getFutureResult(futures.get(1), "EVENT", eventEndpoint);
            SubjectExtractItemResult activity = getFutureResult(futures.get(2), "ACTIVITY", activityEndpoint);

            return new SubjectExtractResult(
                    targetYear,
                    targetProdYear,
                    requestCount,
                    requestOffset,
                    shouldRetryFail,
                    targets.size(),
                    workerCount,
                    policy,
                    event,
                    activity
            );
        } finally {
            executorService.shutdown();
        }
    }

    public SubjectExtractAllResult extractAll(String transferYear, String prodYear, Integer limit, Integer maxLoop, Boolean retryFail) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = normalizeProductionYear(prodYear);
        int batchSize = limit == null || limit <= 0 ? 100 : limit;
        int loopLimit = maxLoop == null || maxLoop <= 0 ? 10_000 : maxLoop;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);
        int loopCount = 0;
        int totalTargetCount = 0;
        SubjectAccumulator policy = new SubjectAccumulator("POLICY", resolveEndpoint(properties.getPolicyEndpoint(), 0));
        SubjectAccumulator event = new SubjectAccumulator("EVENT", resolveEndpoint(properties.getEventEndpoint(), 1));
        SubjectAccumulator activity = new SubjectAccumulator("ACTIVITY", resolveEndpoint(properties.getActivityEndpoint(), 2));
        boolean completed = false;

        while (loopCount < loopLimit) {
            loopCount++;
            SubjectExtractResult result = extractBatch(targetYear, targetProdYear, batchSize, 0, shouldRetryFail);
            totalTargetCount += result.targetCount();
            policy.add(result.policy());
            event.add(result.event());
            activity.add(result.activity());

            log.info("subject-extract/all loop#{} | year={} | prodYear={} | batchSize={} | retryFail={} | target={} | policy={}/{} | event={}/{} | activity={}/{}",
                    loopCount,
                    targetYear,
                    targetProdYear,
                    batchSize,
                    shouldRetryFail,
                    result.targetCount(),
                    result.policy().successCount(),
                    result.policy().failCount(),
                    result.event().successCount(),
                    result.event().failCount(),
                    result.activity().successCount(),
                    result.activity().failCount());

            if (result.targetCount() == 0) {
                completed = true;
                break;
            }
        }

        return new SubjectExtractAllResult(
                targetYear,
                targetProdYear,
                batchSize,
                loopLimit,
                shouldRetryFail,
                loopCount,
                totalTargetCount,
                policy.toResult(),
                event.toResult(),
                activity.toResult(),
                completed
        );
    }

    private Callable<SubjectExtractItemResult> createPolicyTask(String endpoint,
                                                                List<PolicyExtractTarget> targets,
                                                                boolean retryFail) {
        return () -> {
            PromptTemplate promptTemplate = promptTemplateRepository.get("policy_extract");
            int targetCount = 0;
            int success = 0;
            int fail = 0;
            for (PolicyExtractTarget target : targets) {
                if (!shouldProcess(target.getLlmPolicyStatus(), retryFail)) {
                    continue;
                }
                targetCount++;
                try {
                    PolicyExtractResponse result = callPolicyLlm(endpoint, promptTemplate, target);
                    policyExtractMapper.updatePolicyExtractSuccess(target, result);
                    success++;
                } catch (Exception e) {
                    policyExtractMapper.updatePolicyExtractFail(target, cut(safeMessage(e), 100));
                    fail++;
                    log.warn("Subject policy extract FAIL | rcCode={} | rcRfileNo={} | rcRitemNo={} | error={}",
                            target.getRcCode(), target.getRcRfileNo(), target.getRcRitemNo(), safeMessage(e));
                }
            }
            return new SubjectExtractItemResult("POLICY", endpoint, targetCount, success, fail);
        };
    }

    private Callable<SubjectExtractItemResult> createEventTask(String endpoint,
                                                               List<PolicyExtractTarget> targets,
                                                               boolean retryFail) {
        return () -> {
            PromptTemplate promptTemplate = promptTemplateRepository.get("event_extract");
            int targetCount = 0;
            int success = 0;
            int fail = 0;
            for (PolicyExtractTarget target : targets) {
                if (!shouldProcess(target.getLlmEventStatus(), retryFail)) {
                    continue;
                }
                targetCount++;
                try {
                    EventExtractResponse result = callEventLlm(endpoint, promptTemplate, target);
                    eventExtractMapper.updateEventExtractSuccess(target, result);
                    success++;
                } catch (Exception e) {
                    eventExtractMapper.updateEventExtractFail(target, cut(safeMessage(e), 500));
                    fail++;
                    log.warn("Subject event extract FAIL | rcCode={} | rcRfileNo={} | rcRitemNo={} | error={}",
                            target.getRcCode(), target.getRcRfileNo(), target.getRcRitemNo(), safeMessage(e));
                }
            }
            return new SubjectExtractItemResult("EVENT", endpoint, targetCount, success, fail);
        };
    }

    private Callable<SubjectExtractItemResult> createActivityTask(String endpoint,
                                                                  List<PolicyExtractTarget> targets,
                                                                  boolean retryFail) {
        return () -> {
            PromptTemplate promptTemplate = promptTemplateRepository.get("activity_extract");
            int targetCount = 0;
            int success = 0;
            int fail = 0;
            for (PolicyExtractTarget target : targets) {
                if (!shouldProcess(target.getLlmActivityStatus(), retryFail)) {
                    continue;
                }
                targetCount++;
                try {
                    ActivityExtractResponse result = callActivityLlm(endpoint, promptTemplate, target);
                    activityExtractMapper.updateActivityExtractSuccess(target, result);
                    success++;
                } catch (Exception e) {
                    activityExtractMapper.updateActivityExtractFail(target, cut(safeMessage(e), 500));
                    fail++;
                    log.warn("Subject activity extract FAIL | rcCode={} | rcRfileNo={} | rcRitemNo={} | error={}",
                            target.getRcCode(), target.getRcRfileNo(), target.getRcRitemNo(), safeMessage(e));
                }
            }
            return new SubjectExtractItemResult("ACTIVITY", endpoint, targetCount, success, fail);
        };
    }

    private PolicyExtractResponse callPolicyLlm(String endpoint, PromptTemplate promptTemplate, PolicyExtractTarget target) throws Exception {
        String content = callLlm(endpoint, promptTemplate, buildPromptVariables(target, "POLICY", "candidate_policy_list_json"));
        JsonNode root = objectMapper.readTree(extractJson(content));
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

    private EventExtractResponse callEventLlm(String endpoint, PromptTemplate promptTemplate, PolicyExtractTarget target) throws Exception {
        String content = callLlm(endpoint, promptTemplate, buildPromptVariables(target, "EVENT", "candidate_event_list_json"));
        JsonNode root = objectMapper.readTree(extractJson(content));
        return new EventExtractResponse(
                textOrDefault(root, "RC_CODE", target.getRcCode()),
                textOrDefault(root, "RC_RFILE_NO", target.getRcRfileNo()),
                textOrDefault(root, "RC_RITEM_NO", target.getRcRitemNo()),
                textOrDefault(root, "BND_TTL", target.getBndTtl()),
                textOrDefault(root, "JEMOK", target.getJemok()),
                textOrDefault(root, "event_name", textOrDefault(root, "EVENT_NAME", "")),
                textOrDefault(root, "ITEM_CD", ""),
                textOrDefault(root, "reason", textOrDefault(root, "REASON", ""))
        );
    }

    private ActivityExtractResponse callActivityLlm(String endpoint, PromptTemplate promptTemplate, PolicyExtractTarget target) throws Exception {
        String content = callLlm(endpoint, promptTemplate, buildPromptVariables(target, "ACTIVITY", "candidate_activity_list_json"));
        JsonNode root = objectMapper.readTree(extractJson(content));
        return new ActivityExtractResponse(
                textOrDefault(root, "RC_CODE", target.getRcCode()),
                textOrDefault(root, "RC_RFILE_NO", target.getRcRfileNo()),
                textOrDefault(root, "RC_RITEM_NO", target.getRcRitemNo()),
                textOrDefault(root, "BND_TTL", target.getBndTtl()),
                textOrDefault(root, "JEMOK", target.getJemok()),
                textOrDefault(root, "activity_name", textOrDefault(root, "ACTIVITY_NAME", "")),
                textOrDefault(root, "ITEM_CD", ""),
                textOrDefault(root, "reason", textOrDefault(root, "REASON", ""))
        );
    }

    private String callLlm(String endpoint, PromptTemplate promptTemplate, Map<String, Object> variables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        String prompt = promptTemplateRenderer.render(promptTemplate.content(), variables);

        Map<String, Object> payload = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "Return only a JSON object."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2,
                "stream", false
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(payload, headers), Map.class);
        return parseContent(response.getBody());
    }

    private Map<String, Object> buildPromptVariables(PolicyExtractTarget target, String clsCd, String candidateVariableName) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("RC_CODE", nullToEmpty(target.getRcCode()));
        variables.put("RC_RFILE_NO", nullToEmpty(target.getRcRfileNo()));
        variables.put("RC_RITEM_NO", nullToEmpty(target.getRcRitemNo()));
        variables.put("BND_TTL", nullToEmpty(target.getBndTtl()));
        variables.put("JEMOK", nullToEmpty(target.getJemok()));
        variables.put("ALL_ORG_NM", nullToEmpty(target.getAllOrgNm()));
        variables.put("PRODREGDATE", nullToEmpty(target.getProdRegDate()));
        variables.put("PRODYEAR", nullToEmpty(target.getProdYear()));
        variables.put(candidateVariableName, buildCandidateListJson(target, clsCd));
        return variables;
    }

    private String buildCandidateListJson(PolicyExtractTarget target, String clsCd) {
        String productionDate = StringUtils.hasText(target.getProdRegDate())
                ? normalizeProductionDate(target.getProdRegDate())
                : "";
        String productionYear = normalizeProductionYear(target.getProdYear());
        List<SubjectPolicyCandidate> candidates = subjectPolicyMapper.findSubjectCandidates(clsCd, productionDate, productionYear);
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
            throw new IllegalStateException("Failed to serialize " + clsCd + " candidates", e);
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

    private boolean shouldProcess(String status, boolean retryFail) {
        if (retryFail) {
            return !STATUS_PASS.equals(status);
        }
        return !StringUtils.hasText(status);
    }

    private SubjectExtractItemResult getFutureResult(Future<SubjectExtractItemResult> future, String subjectType, String endpoint) {
        try {
            return future.get();
        } catch (Exception e) {
            log.warn("Subject extract worker future failed | subjectType={} | error={}", subjectType, safeMessage(e));
            return new SubjectExtractItemResult(subjectType, endpoint, 0, 0, 1);
        }
    }

    private SubjectExtractItemResult emptyResult(String subjectType, String endpoint) {
        return new SubjectExtractItemResult(subjectType, endpoint, 0, 0, 0);
    }

    private String textOrDefault(JsonNode root, String fieldName, String defaultValue) {
        String value = root.path(fieldName).asText("").trim();
        return StringUtils.hasText(value) ? value : nullToEmpty(defaultValue);
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

    private static class SubjectAccumulator {
        private final String subjectType;
        private final String endpoint;
        private int targetCount;
        private int successCount;
        private int failCount;

        private SubjectAccumulator(String subjectType, String endpoint) {
            this.subjectType = subjectType;
            this.endpoint = endpoint;
        }

        private void add(SubjectExtractItemResult result) {
            targetCount += result.targetCount();
            successCount += result.successCount();
            failCount += result.failCount();
        }

        private SubjectExtractItemResult toResult() {
            return new SubjectExtractItemResult(subjectType, endpoint, targetCount, successCount, failCount);
        }
    }
}
