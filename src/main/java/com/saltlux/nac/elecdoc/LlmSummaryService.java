package com.saltlux.nac.elecdoc;

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
                    String summary = callLlm(endpoint, target);
                    llmSummaryMapper.updateSummarySuccess(target, cut(summary, properties.getMaxSummaryLength()));
                    success++;
                    log.info("LLM summary PASS | workerNo={} | fileName={} | zipSeq={} | rcRfileNo={} | rcRitemNo={}",
                            workerNo, target.getFileName(), target.getZipSeq(), target.getRcRfileNo(), target.getRcRitemNo());
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

    private String callLlm(String endpoint, LlmSummaryTarget target) {
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
        return parseContent(response.getBody());
    }

    private String buildUserPrompt(LlmSummaryTarget target) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음 기록물 문서를 500자 이내로 요약하세요.\n\n");
        sb.append("[철 제목]\n").append(nullToEmpty(target.getBndTtl())).append("\n\n");
        sb.append("[문서 제목]\n").append(nullToEmpty(target.getJemok())).append("\n\n");
        sb.append("[파일명]\n").append(nullToEmpty(target.getFileName()));
        if (StringUtils.hasText(target.getZipEntryFileName())) {
            sb.append(" / ZIP 내부파일: ").append(target.getZipEntryFileName());
        }
        sb.append("\n\n[본문]\n").append(cut(nullToEmpty(target.getIndexingContents()), properties.getMaxContentLength()));
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
}
