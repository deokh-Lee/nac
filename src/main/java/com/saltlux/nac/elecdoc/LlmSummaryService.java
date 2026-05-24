package com.saltlux.nac.elecdoc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmSummaryService(LlmSummaryMapper llmSummaryMapper,
                             DocumentLlmProperties properties,
                             RestTemplateBuilder restTemplateBuilder) {
        this.llmSummaryMapper = llmSummaryMapper;
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    public LlmSummaryBatchResult summarizeBatch(String transferYear, Integer limit, Boolean retryFail) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        int workerCount = Math.max(1, properties.getEndpoints().size());
        int perWorkerSize = Math.max(1, properties.getPerWorkerSize());
        int requestCount = limit == null || limit <= 0 ? workerCount * perWorkerSize : limit;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);

        List<LlmSummaryTarget> targets = llmSummaryMapper.findSummaryTargets(targetYear, requestCount, shouldRetryFail);
        if (targets.isEmpty()) {
            return new LlmSummaryBatchResult(targetYear, requestCount, 0, workerCount, perWorkerSize, 0, 0);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        List<Future<WorkerResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workerCount; i++) {
                int fromIndex = i * perWorkerSize;
                if (fromIndex >= targets.size()) {
                    break;
                }
                int toIndex = Math.min(fromIndex + perWorkerSize, targets.size());
                String endpoint = properties.getEndpoints().get(i);
                List<LlmSummaryTarget> workerTargets = targets.subList(fromIndex, toIndex);
                futures.add(executorService.submit(createWorkerTask(i + 1, endpoint, workerTargets)));
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

            return new LlmSummaryBatchResult(targetYear, requestCount, targets.size(), workerCount, perWorkerSize, success, fail);
        } finally {
            executorService.shutdown();
        }
    }

    private Callable<WorkerResult> createWorkerTask(int workerNo, String endpoint, List<LlmSummaryTarget> targets) {
        return () -> {
            int success = 0;
            int fail = 0;
            log.info("LLM worker start | workerNo={} | endpoint={} | size={}", workerNo, endpoint, targets.size());

            for (LlmSummaryTarget target : targets) {
                try {
                    LlmSummaryResponse llmResult = callLlm(endpoint, target);
                    llmSummaryMapper.updateSummarySuccess(
                            target,
                            llmResult.flag(),
                            cut(llmResult.summary(), properties.getMaxSummaryLength())
                    );
                    success++;
                    log.info("LLM summary PASS | workerNo={} | flag={} | fileName={} | zipSeq={} | rcRfileNo={} | rcRitemNo={}",
                            workerNo, llmResult.flag(), target.getFileName(), target.getZipSeq(), target.getRcRfileNo(), target.getRcRitemNo());
                } catch (Exception e) {
                    llmSummaryMapper.updateSummaryFail(target, cut(safeMessage(e), 2000));
                    fail++;
                    log.warn("LLM summary FAIL | workerNo={} | fileName={} | zipSeq={} | rcRfileNo={} | rcRitemNo={} | error={}",
                            workerNo, target.getFileName(), target.getZipSeq(), target.getRcRfileNo(), target.getRcRitemNo(), safeMessage(e));
                }
            }

            log.info("LLM worker end | workerNo={} | success={} | fail={}", workerNo, success, fail);
            return new WorkerResult(success, fail);
        };
    }

    private LlmSummaryResponse callLlm(String endpoint, LlmSummaryTarget target) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", properties.getSystemPrompt()),
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
