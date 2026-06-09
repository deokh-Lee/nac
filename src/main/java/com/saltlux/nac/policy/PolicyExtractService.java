package com.saltlux.nac.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saltlux.nac.elecdoc.DocumentLlmProperties;
import com.saltlux.nac.prompt.PromptTemplate;
import com.saltlux.nac.prompt.PromptTemplateRenderer;
import com.saltlux.nac.prompt.PromptTemplateRepository;
import com.saltlux.nac.progress.LlmExtractProgressService;
import com.saltlux.nac.progress.LlmPolicyMappingResult;
import com.saltlux.nac.subject.PolicyDetailMappingDictionary;
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
    private static final String DEPARTMENT_PROMPT_NAME = "policy_extract_dept";
    private static final List<String> SAMPLE_DEPARTMENTS = List.of("환경부", "보건복지부", "법제처");
    private static final List<String> POLICY_DEPARTMENTS = List.of("환경부", "보건복지부");

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
    private final ConcurrentMap<String, String> mappingDictionaryJsonCache = new ConcurrentHashMap<>();

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

    public List<PolicyExtractTarget> findDepartmentTargets(String transferYear, String prodYear, Integer limit, Integer offset) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int requestCount = limit == null || limit <= 0 ? 100 : limit;
        int requestOffset = offset == null || offset < 0 ? 0 : offset;
        return policyExtractMapper.findPolicyDepartmentTargets(
                targetYear,
                targetProdYear,
                POLICY_DEPARTMENTS,
                requestCount,
                requestOffset
        );
    }

    public List<PolicyExtractTarget> findDepartmentItemCompareTargets(String transferYear, String prodYear, Integer limit, Integer offset) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int requestCount = limit == null || limit <= 0 ? 100 : limit;
        int requestOffset = offset == null || offset < 0 ? 0 : offset;
        return policyExtractMapper.findPolicyDepartmentItemCompareTargets(
                targetYear,
                targetProdYear,
                POLICY_DEPARTMENTS,
                requestCount,
                requestOffset
        );
    }

    public PolicyExtractResult extractDepartmentFileBatch(String transferYear, String prodYear, Integer limit, Integer offset) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int requestCount = limit == null || limit <= 0 ? 100 : limit;
        int requestOffset = offset == null || offset < 0 ? 0 : offset;
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();

        List<PolicyExtractTarget> fileTargets = policyExtractMapper.findPolicyDepartmentFileTargets(
                targetYear,
                targetProdYear,
                POLICY_DEPARTMENTS,
                requestCount,
                requestOffset
        );
        if (fileTargets.isEmpty()) {
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, false, 0, workerCount, 0, 0);
        }

        PromptTemplate promptTemplate = promptTemplateRepository.get(DEPARTMENT_PROMPT_NAME);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;
        int successCount = 0;
        int failCount = 0;
        try {
            runId = progressService.startRun("POLICY_DEPT_FILE_BATCH", targetYear, targetProdYear, false, requestCount, 1);
            WorkerResult result = runDepartmentFileWorkerBatch(executorService, endpoints, promptTemplate, fileTargets, runId, targetYear, targetProdYear);
            successCount = result.successCount();
            failCount = result.failCount();

            progressService.finishRun(runId, true, 1, fileTargets.size(), successCount, failCount);
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, false,
                    fileTargets.size(), workerCount, successCount, failCount);
        } catch (RuntimeException e) {
            progressService.failRun(runId, 1, fileTargets.size(), successCount, failCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
        }
    }

    public PolicyExtractResult extractDepartmentItemCompareBatch(String transferYear, String prodYear, Integer limit, Integer offset) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int requestCount = limit == null || limit <= 0 ? 100 : limit;
        int requestOffset = offset == null || offset < 0 ? 0 : offset;
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();

        List<PolicyExtractTarget> itemTargets = policyExtractMapper.findPolicyDepartmentItemCompareTargets(
                targetYear,
                targetProdYear,
                POLICY_DEPARTMENTS,
                requestCount,
                requestOffset
        );
        if (itemTargets.isEmpty()) {
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, false, 0, workerCount, 0, 0);
        }

        PromptTemplate promptTemplate = promptTemplateRepository.get(DEPARTMENT_PROMPT_NAME);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;
        int successCount = 0;
        int failCount = 0;
        try {
            runId = progressService.startRun("POLICY_DEPT_ITEM_COMPARE_BATCH", targetYear, targetProdYear, false, requestCount, 1);
            WorkerResult result = runDepartmentWorkerBatch(executorService, endpoints, promptTemplate, itemTargets, runId);
            successCount = result.successCount();
            failCount = result.failCount();

            progressService.finishRun(runId, true, 1, itemTargets.size(), successCount, failCount);
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, false,
                    itemTargets.size(), workerCount, successCount, failCount);
        } catch (RuntimeException e) {
            progressService.failRun(runId, 1, itemTargets.size(), successCount, failCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
        }
    }

    public PolicyExtractResult extractDepartmentBatch(String transferYear, String prodYear, Integer limit, Integer offset) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int requestCount = limit == null || limit <= 0 ? 100 : limit;
        int requestOffset = offset == null || offset < 0 ? 0 : offset;
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();

        List<PolicyExtractTarget> fileTargets = policyExtractMapper.findPolicyDepartmentFileTargets(
                targetYear,
                targetProdYear,
                POLICY_DEPARTMENTS,
                requestCount,
                requestOffset
        );
        PromptTemplate promptTemplate = promptTemplateRepository.get(DEPARTMENT_PROMPT_NAME);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;
        int successCount = 0;
        int failCount = 0;
        int totalTargetCount = 0;
        try {
            runId = progressService.startRun("POLICY_DEPT_BATCH", targetYear, targetProdYear, false, requestCount, 1);
            if (!fileTargets.isEmpty()) {
                WorkerResult fileResult = runDepartmentFileWorkerBatch(executorService, endpoints, promptTemplate, fileTargets, runId, targetYear, targetProdYear);
                successCount += fileResult.successCount();
                failCount += fileResult.failCount();
            }

            List<PolicyExtractTarget> itemTargets = policyExtractMapper.findPolicyDepartmentItemCompareTargets(
                    targetYear,
                    targetProdYear,
                    POLICY_DEPARTMENTS,
                    requestCount,
                    requestOffset
            );
            WorkerResult itemResult = itemTargets.isEmpty()
                    ? new WorkerResult(0, 0)
                    : runDepartmentWorkerBatch(executorService, endpoints, promptTemplate, itemTargets, runId);
            successCount += itemResult.successCount();
            failCount += itemResult.failCount();
            totalTargetCount = fileTargets.size() + itemTargets.size();

            progressService.finishRun(runId, true, 1, totalTargetCount, successCount, failCount);
            return new PolicyExtractResult(targetYear, targetProdYear, requestCount, requestOffset, false,
                    totalTargetCount, workerCount, successCount, failCount);
        } catch (RuntimeException e) {
            progressService.failRun(runId, 1, totalTargetCount, successCount, failCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
        }
    }

    public PolicyExtractAllResult extractDepartmentFileAll(String transferYear, String prodYear, Integer limit, Integer maxLoop) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int batchSize = limit == null || limit <= 0 ? 100 : limit;
        int loopLimit = maxLoop == null || maxLoop <= 0 ? 10_000 : maxLoop;
        SubjectExtractRunRegistry.RunKey runKey = runRegistry.acquire(
                "policy-extract/departments/file/all",
                SubjectExtractRunRegistry.POLICY,
                targetYear,
                targetProdYear
        );
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();
        PromptTemplate promptTemplate = promptTemplateRepository.get(DEPARTMENT_PROMPT_NAME);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;
        int loopCount = 0;
        int totalTargetCount = 0;
        int totalSuccessCount = 0;
        int totalFailCount = 0;
        boolean completed = false;

        try {
            runId = progressService.startRun("POLICY_DEPT_FILE_ALL", targetYear, targetProdYear, false, batchSize, loopLimit);
            while (loopCount < loopLimit) {
                int requestOffset = loopCount * batchSize;
                loopCount++;
                List<PolicyExtractTarget> fileTargets = policyExtractMapper.findPolicyDepartmentFileTargets(
                        targetYear,
                        targetProdYear,
                        POLICY_DEPARTMENTS,
                        batchSize,
                        requestOffset
                );
                WorkerResult result = fileTargets.isEmpty()
                        ? new WorkerResult(0, 0)
                        : runDepartmentFileWorkerBatch(executorService, endpoints, promptTemplate, fileTargets, runId, targetYear, targetProdYear);
                totalTargetCount += fileTargets.size();
                totalSuccessCount += result.successCount();
                totalFailCount += result.failCount();

                log.info("policy-extract/departments/file/all loop#{} | year={} | prodYear={} | batchSize={} | offset={} | target={} | success={} | fail={} | totalTarget={} | totalSuccess={} | totalFail={}",
                        loopCount, targetYear, targetProdYear, batchSize, requestOffset, fileTargets.size(),
                        result.successCount(), result.failCount(), totalTargetCount, totalSuccessCount, totalFailCount);
                progressService.updateRunProgress(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);

                if (fileTargets.isEmpty()) {
                    completed = true;
                    break;
                }
            }

            progressService.finishRun(runId, completed, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);
            return new PolicyExtractAllResult(targetYear, targetProdYear, batchSize, loopLimit, false,
                    loopCount, totalTargetCount, totalSuccessCount, totalFailCount, completed);
        } catch (RuntimeException e) {
            progressService.failRun(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
            runRegistry.release(runKey);
        }
    }

    public PolicyExtractAllResult extractDepartmentItemCompareAll(String transferYear, String prodYear, Integer limit, Integer maxLoop) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int batchSize = limit == null || limit <= 0 ? 100 : limit;
        int loopLimit = maxLoop == null || maxLoop <= 0 ? 10_000 : maxLoop;
        SubjectExtractRunRegistry.RunKey runKey = runRegistry.acquire(
                "policy-extract/departments/items/all",
                SubjectExtractRunRegistry.POLICY,
                targetYear,
                targetProdYear
        );
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();
        PromptTemplate promptTemplate = promptTemplateRepository.get(DEPARTMENT_PROMPT_NAME);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;
        int loopCount = 0;
        int totalTargetCount = 0;
        int totalSuccessCount = 0;
        int totalFailCount = 0;
        boolean completed = false;

        try {
            runId = progressService.startRun("POLICY_DEPT_ITEM_COMPARE_ALL", targetYear, targetProdYear, false, batchSize, loopLimit);
            while (loopCount < loopLimit) {
                loopCount++;
                List<PolicyExtractTarget> itemTargets = policyExtractMapper.findPolicyDepartmentItemCompareTargets(
                        targetYear,
                        targetProdYear,
                        POLICY_DEPARTMENTS,
                        batchSize,
                        0
                );
                WorkerResult result = itemTargets.isEmpty()
                        ? new WorkerResult(0, 0)
                        : runDepartmentWorkerBatch(executorService, endpoints, promptTemplate, itemTargets, runId);
                totalTargetCount += itemTargets.size();
                totalSuccessCount += result.successCount();
                totalFailCount += result.failCount();

                log.info("policy-extract/departments/items/all loop#{} | year={} | prodYear={} | batchSize={} | target={} | success={} | fail={} | totalTarget={} | totalSuccess={} | totalFail={}",
                        loopCount, targetYear, targetProdYear, batchSize, itemTargets.size(),
                        result.successCount(), result.failCount(), totalTargetCount, totalSuccessCount, totalFailCount);
                progressService.updateRunProgress(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);

                if (itemTargets.isEmpty()) {
                    completed = true;
                    break;
                }
            }

            progressService.finishRun(runId, completed, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);
            return new PolicyExtractAllResult(targetYear, targetProdYear, batchSize, loopLimit, false,
                    loopCount, totalTargetCount, totalSuccessCount, totalFailCount, completed);
        } catch (RuntimeException e) {
            progressService.failRun(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
            runRegistry.release(runKey);
        }
    }

    public PolicyExtractAllResult extractDepartmentAll(String transferYear, String prodYear, Integer limit, Integer maxLoop) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : "2023";
        String targetProdYear = StringUtils.hasText(prodYear) ? normalizeProductionYear(prodYear) : "2012";
        int batchSize = limit == null || limit <= 0 ? 100 : limit;
        int loopLimit = maxLoop == null || maxLoop <= 0 ? 10_000 : maxLoop;
        SubjectExtractRunRegistry.RunKey runKey = runRegistry.acquire(
                "policy-extract/departments/all",
                SubjectExtractRunRegistry.POLICY,
                targetYear,
                targetProdYear
        );
        List<String> endpoints = resolvePolicyEndpoints();
        int workerCount = endpoints.size();
        PromptTemplate promptTemplate = promptTemplateRepository.get(DEPARTMENT_PROMPT_NAME);
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        Long runId = null;
        int loopCount = 0;
        int totalTargetCount = 0;
        int totalSuccessCount = 0;
        int totalFailCount = 0;
        boolean fileCompleted = false;
        boolean itemCompleted = false;

        try {
            runId = progressService.startRun("POLICY_DEPT_ALL", targetYear, targetProdYear, false, batchSize, loopLimit);
            while (loopCount < loopLimit) {
                int requestOffset = loopCount * batchSize;
                loopCount++;
                List<PolicyExtractTarget> fileTargets = policyExtractMapper.findPolicyDepartmentFileTargets(
                        targetYear,
                        targetProdYear,
                        POLICY_DEPARTMENTS,
                        batchSize,
                        requestOffset
                );
                WorkerResult result = fileTargets.isEmpty()
                        ? new WorkerResult(0, 0)
                        : runDepartmentFileWorkerBatch(executorService, endpoints, promptTemplate, fileTargets, runId, targetYear, targetProdYear);
                totalTargetCount += fileTargets.size();
                totalSuccessCount += result.successCount();
                totalFailCount += result.failCount();

                log.info("policy-extract/departments/all file loop#{} | year={} | prodYear={} | batchSize={} | offset={} | target={} | success={} | fail={} | totalTarget={} | totalSuccess={} | totalFail={}",
                        loopCount, targetYear, targetProdYear, batchSize, requestOffset, fileTargets.size(),
                        result.successCount(), result.failCount(), totalTargetCount, totalSuccessCount, totalFailCount);
                progressService.updateRunProgress(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);

                if (fileTargets.isEmpty()) {
                    fileCompleted = true;
                    break;
                }
            }

            while (fileCompleted && loopCount < loopLimit) {
                loopCount++;
                List<PolicyExtractTarget> itemTargets = policyExtractMapper.findPolicyDepartmentItemCompareTargets(
                        targetYear,
                        targetProdYear,
                        POLICY_DEPARTMENTS,
                        batchSize,
                        0
                );
                WorkerResult result = itemTargets.isEmpty()
                        ? new WorkerResult(0, 0)
                        : runDepartmentWorkerBatch(executorService, endpoints, promptTemplate, itemTargets, runId);
                totalTargetCount += itemTargets.size();
                totalSuccessCount += result.successCount();
                totalFailCount += result.failCount();

                log.info("policy-extract/departments/all item loop#{} | year={} | prodYear={} | batchSize={} | target={} | success={} | fail={} | totalTarget={} | totalSuccess={} | totalFail={}",
                        loopCount, targetYear, targetProdYear, batchSize, itemTargets.size(),
                        result.successCount(), result.failCount(), totalTargetCount, totalSuccessCount, totalFailCount);
                progressService.updateRunProgress(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);

                if (itemTargets.isEmpty()) {
                    itemCompleted = true;
                    break;
                }
            }

            boolean completed = fileCompleted && itemCompleted;
            progressService.finishRun(runId, completed, loopCount, totalTargetCount, totalSuccessCount, totalFailCount);
            return new PolicyExtractAllResult(targetYear, targetProdYear, batchSize, loopLimit, false,
                    loopCount, totalTargetCount, totalSuccessCount, totalFailCount, completed);
        } catch (RuntimeException e) {
            progressService.failRun(runId, loopCount, totalTargetCount, totalSuccessCount, totalFailCount, safeMessage(e));
            throw e;
        } finally {
            executorService.shutdown();
            runRegistry.release(runKey);
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

    private WorkerResult runDepartmentFileWorkerBatch(ExecutorService executorService,
                                                      List<String> endpoints,
                                                      PromptTemplate promptTemplate,
                                                      List<PolicyExtractTarget> targets,
                                                      Long runId,
                                                      String transferYear,
                                                      String prodYear) {
        int workerCount = endpoints.size();
        List<List<PolicyExtractTarget>> workerBuckets = distributeRoundRobin(targets, workerCount);
        List<Future<WorkerResult>> futures = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            List<PolicyExtractTarget> workerTargets = workerBuckets.get(i);
            if (workerTargets.isEmpty()) {
                continue;
            }
            String endpoint = endpoints.get(i);
            log.info("Policy department file worker assigned | workerNo={} | endpoint={} | size={}",
                    i + 1, endpoint, workerTargets.size());
            futures.add(executorService.submit(createDepartmentFileWorkerTask(i + 1, endpoint, promptTemplate, workerTargets, runId, transferYear, prodYear)));
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
                log.warn("Policy department file worker future failed. error={}", safeMessage(e));
            }
        }
        return new WorkerResult(success, fail);
    }

    private Callable<WorkerResult> createDepartmentFileWorkerTask(int workerNo,
                                                                  String endpoint,
                                                                  PromptTemplate promptTemplate,
                                                                  List<PolicyExtractTarget> targets,
                                                                  Long runId,
                                                                  String transferYear,
                                                                  String prodYear) {
        return () -> {
            int success = 0;
            int fail = 0;
            log.info("Policy department file worker start | workerNo={} | endpoint={} | size={}", workerNo, endpoint, targets.size());

            for (PolicyExtractTarget target : targets) {
                try {
                    DepartmentLlmResult llmResult = callDepartmentLlm(endpoint, promptTemplate, fileLevelTarget(target));
                    PolicyExtractResponse result = llmResult.policy();
                    if (!isDeterminedPolicy(result)) {
                        progressService.recordPolicyMappingSuccess(runId, "POLICY_DEPT_FILE", target, result.itemCd(), result.policy(), llmResult.mappingResult());
                        success++;
                        log.info("Policy department file skipped | workerNo={} | rcCode={} | rcRfileNo={} | policy={} | itemCd={} | reason={}",
                                workerNo, target.getRcCode(), target.getRcRfileNo(),
                                cut(result.policy(), 100), result.itemCd(), cut(result.reason(), 200));
                        continue;
                    }

                    int affectedRows = policyExtractMapper.updatePolicyDepartmentFileSuccess(target, result);
                    List<PolicyExtractTarget> itemTargets = policyExtractMapper.findPolicyDepartmentFileItemTargets(
                            transferYear,
                            prodYear,
                            target.getRcRfileNo()
                    );
                    for (PolicyExtractTarget itemTarget : itemTargets) {
                        progressService.recordPolicyMappingSuccess(
                                runId,
                                "POLICY_DEPT_FILE",
                                itemTarget,
                                result.itemCd(),
                                result.policy(),
                                mappingResultForTarget(llmResult.mappingResult(), itemTarget)
                        );
                    }
                    success++;
                    log.info("Policy department file result | status=PASS | workerNo={} | rcCode={} | rcRfileNo={} | affectedRows={} | itemCd={} | value={} | reason={}",
                            workerNo, target.getRcCode(), target.getRcRfileNo(), affectedRows,
                            result.itemCd(), cut(result.policy(), 100), cut(result.reason(), 200));
                } catch (Exception e) {
                    progressService.recordFail(runId, "POLICY_DEPT_FILE", target, safeMessage(e));
                    fail++;
                    log.warn("Policy department file result | status=FAIL | workerNo={} | rcCode={} | rcRfileNo={} | error={}",
                            workerNo, target.getRcCode(), target.getRcRfileNo(), cut(safeMessage(e), 4000));
                }
            }

            log.info("Policy department file worker end | workerNo={} | success={} | fail={}", workerNo, success, fail);
            return new WorkerResult(success, fail);
        };
    }

    private WorkerResult runDepartmentWorkerBatch(ExecutorService executorService,
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
            log.info("Policy department extract worker assigned | workerNo={} | endpoint={} | size={}",
                    i + 1, endpoint, workerTargets.size());
            futures.add(executorService.submit(createDepartmentWorkerTask(i + 1, endpoint, promptTemplate, workerTargets, runId)));
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
                log.warn("Policy department extract worker future failed. error={}", safeMessage(e));
            }
        }
        return new WorkerResult(success, fail);
    }

    private Callable<WorkerResult> createDepartmentWorkerTask(int workerNo,
                                                              String endpoint,
                                                              PromptTemplate promptTemplate,
                                                              List<PolicyExtractTarget> targets,
                                                              Long runId) {
        return () -> {
            int success = 0;
            int fail = 0;
            log.info("Policy department extract worker start | workerNo={} | endpoint={} | size={}", workerNo, endpoint, targets.size());

            for (PolicyExtractTarget target : targets) {
                try {
                    DepartmentLlmResult llmResult = callDepartmentLlm(endpoint, promptTemplate, target);
                    PolicyExtractResponse result = llmResult.policy();
                    progressService.recordPolicyMappingSuccess(runId, "POLICY_DEPT", target, result.itemCd(), result.policy(), llmResult.mappingResult());
                    if (!isDeterminedPolicy(result)) {
                        policyExtractMapper.updatePolicyExtractSuccess(target, result);
                        success++;
                        log.info("Policy department extract record result | status=PASS_NO_POLICY | workerNo={} | rcCode={} | rcRfileNo={} | rcRitemNo={} | itemCd={} | value={} | reason={}",
                                workerNo, target.getRcCode(), target.getRcRfileNo(), target.getRcRitemNo(),
                                result.itemCd(), cut(result.policy(), 100), cut(result.reason(), 200));
                        continue;
                    }

                    policyExtractMapper.updatePolicyExtractSuccess(target, result);
                    success++;
                    log.info("Policy department extract record result | status=PASS | workerNo={} | rcCode={} | rcRfileNo={} | rcRitemNo={} | itemCd={} | value={} | reason={}",
                            workerNo, target.getRcCode(), target.getRcRfileNo(), target.getRcRitemNo(),
                            result.itemCd(), cut(result.policy(), 100), cut(result.reason(), 200));
                } catch (Exception e) {
                    policyExtractMapper.updatePolicyExtractFail(target, cut(safeMessage(e), 100));
                    progressService.recordFail(runId, "POLICY_DEPT", target, safeMessage(e));
                    fail++;
                    log.warn("Policy department extract record result | status=FAIL | workerNo={} | rcCode={} | rcRfileNo={} | rcRitemNo={} | error={}",
                            workerNo, target.getRcCode(), target.getRcRfileNo(), target.getRcRitemNo(), cut(safeMessage(e), 4000));
                }
            }

            log.info("Policy department extract worker end | workerNo={} | success={} | fail={}", workerNo, success, fail);
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

    private DepartmentLlmResult callDepartmentLlm(String endpoint, PromptTemplate promptTemplate, PolicyExtractTarget target) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        String prompt = promptTemplateRenderer.render(promptTemplate.content(), buildDepartmentPromptVariables(target));

        Map<String, Object> payload = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "반드시 JSON만 출력하세요."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2,
                "stream", false
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(payload, headers), Map.class);
        String content = parseContent(response.getBody());
        return parsePolicyDepartmentJson(content, target);
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

    private Map<String, Object> buildDepartmentPromptVariables(PolicyExtractTarget target) {
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
        variables.put("Mapping_Dictionary", buildMappingDictionaryJson(target));
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

    private String buildMappingDictionaryJson(PolicyExtractTarget target) {
        String leadAgencyPrefix = resolveLeadAgencyPrefix(target);
        return mappingDictionaryJsonCache.computeIfAbsent(leadAgencyPrefix, this::loadMappingDictionaryJson);
    }

    private String loadMappingDictionaryJson(String leadAgencyPrefix) {
        List<PolicyDetailMappingDictionary> dictionaries =
                subjectPolicyMapper.findPolicyDetailMappingDictionary(leadAgencyPrefix);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PolicyDetailMappingDictionary dictionary : dictionaries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("GOV_TASK_NO", dictionary.getGovTaskNo());
            row.put("GOV_TASK_NM", dictionary.getGovTaskNm());
            row.put("DETAIL_TASK_CD", dictionary.getDetailTaskCd());
            row.put("DETAIL_TASK_NM", dictionary.getDetailTaskNm());
            row.put("DIRECT_MAPPING_TERMS", dictionary.getDirectMappingTerms());
            row.put("CONDITIONAL_MAPPING_TERMS", dictionary.getConditionalMappingTerms());
            row.put("EXPAND_CANDIDATE_TERMS", dictionary.getExpandCandidateTerms());
            row.put("REQUIRED_COMBINE_TERMS", dictionary.getRequiredCombineTerms());
            row.put("BLOCK_TERMS", dictionary.getBlockTerms());
            row.put("NOTE", dictionary.getNote());
            row.put("LEAD_AGENCY", dictionary.getLeadAgency());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize mapping dictionary for " + leadAgencyPrefix, e);
        }
    }

    private String resolveLeadAgencyPrefix(PolicyExtractTarget target) {
        String department = StringUtils.hasText(target.getDeptNm())
                ? target.getDeptNm()
                : firstToken(target.getAllOrgNm());
        if (department.startsWith("환경부")) {
            return "환경부";
        }
        if (department.startsWith("복지부")) {
            return "복지부";
        }
        throw new IllegalStateException("Unsupported policy department: " + department);
    }

    @SuppressWarnings("unchecked")
    private String parseContent(Map body) {
        if (body == null) {
            throw new IllegalStateException("LLM response body is empty");
        }
        Object responseData = body.get("response_data");
        if (responseData instanceof Map<?, ?> responseDataMap) {
            return parseContent((Map) responseDataMap);
        }
        if (responseData != null) {
            return parseContentString(responseData.toString());
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

    private String parseContentString(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("LLM response content is empty");
        }

        String trimmed = value.trim();
        int responseDataIndex = trimmed.indexOf("response_data:");
        if (responseDataIndex >= 0) {
            trimmed = trimmed.substring(responseDataIndex + "response_data:".length()).trim();
        }

        if (trimmed.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                String content = extractChoiceContent(root);
                if (StringUtils.hasText(content)) {
                    return content;
                }
                JsonNode responseData = root.path("response_data");
                if (!responseData.isMissingNode() && !responseData.isNull()) {
                    if (responseData.isTextual()) {
                        return parseContentString(responseData.asText());
                    }
                    content = extractChoiceContent(responseData);
                    if (StringUtils.hasText(content)) {
                        return content;
                    }
                }
            } catch (Exception ignored) {
                // If this is already the assistant JSON content, downstream JSON extraction will handle it.
            }
        }

        return trimmed;
    }

    private String extractChoiceContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode first = choices.get(0);
        String messageContent = first.path("message").path("content").asText("").trim();
        if (StringUtils.hasText(messageContent)) {
            return messageContent;
        }
        return first.path("text").asText("").trim();
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

    private DepartmentLlmResult parsePolicyDepartmentJson(String content, PolicyExtractTarget target) throws Exception {
        JsonNode root = parseLlmJson("POLICY_DEPT", content);
        JsonNode row = root.isArray() ? firstObject(root) : root;
        if (row == null || !row.isObject()) {
            throw new IllegalStateException("LLM POLICY_DEPT response has no JSON object: " + cut(content, 4000));
        }

        PolicyExtractResponse policy = new PolicyExtractResponse(
                textOrDefault(row, "RC_CODE", target.getRcCode()),
                textOrDefault(row, "RC_RFILE_NO", target.getRcRfileNo()),
                textOrDefault(row, "RC_RITEM_NO", target.getRcRitemNo()),
                textOrDefault(row, "BND_TTL", target.getBndTtl()),
                textOrDefault(row, "JEMOK", target.getJemok()),
                firstText(row, "DETAIL_TASK_NM", "detailTaskNm", "후보세부실천과제", "후보 세부실천과제", "후보 이행실천과제", "세부실천과제명", "이행실천과제명"),
                firstText(row, "DETAIL_TASK_CD", "detailTaskCd", "세부실천과제코드", "이행실천과제코드"),
                firstText(row, "REASON", "reason", "판단근거", "판정근거", "매칭사유", "검토·제외 사유")
        );
        return new DepartmentLlmResult(policy, buildPolicyMappingResult(row, target));
    }

    private LlmPolicyMappingResult buildPolicyMappingResult(JsonNode row, PolicyExtractTarget target) throws Exception {
        LlmPolicyMappingResult result = new LlmPolicyMappingResult();
        result.setOrgNm(firstTextOrDefault(row, nullToEmpty(target.getAllOrgNm()), "기관명", "ALL_ORG_NM"));
        result.setProdYear(firstTextOrDefault(row, nullToEmpty(target.getProdYear()), "생산연도", "PRODYEAR"));
        result.setRecordFileNo(firstTextOrDefault(row, nullToEmpty(target.getRcRfileNo()), "기록물철번호", "기록물 철번호", "RC_RFILE_NO"));
        result.setRecordItemNo(firstTextOrDefault(row, nullToEmpty(target.getRcRitemNo()), "기록물건번호", "기록물 건번호", "RC_RITEM_NO"));
        result.setRecordFileTitle(firstTextOrDefault(row, nullToEmpty(target.getBndTtl()), "기록물철명", "기록물 철명", "BND_TTL"));
        result.setRecordItemTitle(firstTextOrDefault(row, nullToEmpty(target.getJemok()), "기록물건명", "기록물 건명", "JEMOK"));
        result.setGovTaskNo(firstText(row, "후보 국정과제번호", "국정과제번호", "GOV_TASK_NO", "govTaskNo"));
        result.setCandidateGovTask(firstText(row, "후보국정과제", "후보 국정과제", "GOV_TASK_NM", "govTaskNm"));
        result.setCandidateDetailTask(firstText(row, "후보세부실천과제", "후보 세부실천과제", "후보 이행실천과제", "DETAIL_TASK_NM", "detailTaskNm"));
        result.setDetailTaskCd(firstText(row, "세부실천과제코드", "이행실천과제코드", "DETAIL_TASK_CD", "detailTaskCd"));
        result.setMatchingTerms(jsonOrText(row.path("매칭어")));
        result.setMatchingSource(firstText(row, "매칭어출처", "매칭어 출처", "matchingSource"));
        result.setCriteriaType(firstText(row, "기준어유형", "기준어 유형", "criteriaType"));
        result.setDecisionValue(firstText(row, "판정값", "decisionValue"));
        result.setDecisionReason(firstText(row, "판단근거", "판정근거", "reason"));
        result.setReviewExcludeReason(firstText(row, "검토·제외사유", "검토·제외 사유", "reviewExcludeReason"));
        result.setResultJson(objectMapper.writeValueAsString(row));
        return result;
    }

    private LlmPolicyMappingResult mappingResultForTarget(LlmPolicyMappingResult source, PolicyExtractTarget target) {
        LlmPolicyMappingResult result = new LlmPolicyMappingResult();
        result.setOrgNm(nullToEmpty(target.getAllOrgNm()));
        result.setProdYear(nullToEmpty(target.getProdYear()));
        result.setRecordFileNo(nullToEmpty(target.getRcRfileNo()));
        result.setRecordItemNo(nullToEmpty(target.getRcRitemNo()));
        result.setRecordFileTitle(nullToEmpty(target.getBndTtl()));
        result.setRecordItemTitle(nullToEmpty(target.getJemok()));
        if (source != null) {
            result.setGovTaskNo(source.getGovTaskNo());
            result.setCandidateGovTask(source.getCandidateGovTask());
            result.setCandidateDetailTask(source.getCandidateDetailTask());
            result.setDetailTaskCd(source.getDetailTaskCd());
            result.setMatchingTerms(source.getMatchingTerms());
            result.setMatchingSource(source.getMatchingSource());
            result.setCriteriaType(source.getCriteriaType());
            result.setDecisionValue(source.getDecisionValue());
            result.setDecisionReason(source.getDecisionReason());
            result.setReviewExcludeReason(source.getReviewExcludeReason());
            result.setResultJson(source.getResultJson());
        }
        return result;
    }

    private JsonNode firstObject(JsonNode root) {
        if (root == null || !root.isArray()) {
            return null;
        }
        for (JsonNode item : root) {
            if (item != null && item.isObject()) {
                return item;
            }
        }
        return null;
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("LLM response content is empty");
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        boolean arrayResponse = arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart);
        int start = arrayResponse ? arrayStart : objectStart;
        int end = arrayResponse ? trimmed.lastIndexOf(']') : trimmed.lastIndexOf('}');
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

    private String firstText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = root.path(fieldName).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String firstTextOrDefault(JsonNode root, String defaultValue, String... fieldNames) {
        String value = firstText(root, fieldNames);
        return StringUtils.hasText(value) ? value : nullToEmpty(defaultValue);
    }

    private String jsonOrText(JsonNode node) throws Exception {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isArray() || node.isObject()) {
            return objectMapper.writeValueAsString(node);
        }
        return node.asText("").trim();
    }

    private boolean isDeterminedPolicy(PolicyExtractResponse result) {
        if (result == null || !StringUtils.hasText(result.itemCd()) || !StringUtils.hasText(result.policy())) {
            return false;
        }
        return !"해당없음".equals(result.policy().trim());
    }

    private PolicyExtractTarget fileLevelTarget(PolicyExtractTarget source) {
        PolicyExtractTarget target = new PolicyExtractTarget();
        target.setRcCode(source.getRcCode());
        target.setRcRfileNo(source.getRcRfileNo());
        target.setRcRitemNo("");
        target.setClssId(source.getClssId());
        target.setBndTtl(source.getBndTtl());
        target.setJemok("");
        target.setAllOrgNm(source.getAllOrgNm());
        target.setDeptNm(source.getDeptNm());
        target.setProdRegDate(source.getProdRegDate());
        target.setProdYear(source.getProdYear());
        return target;
    }

    private List<PolicyExtractTarget> distinctRecordFileTargets(List<PolicyExtractTarget> targets) {
        Map<String, PolicyExtractTarget> distinct = new LinkedHashMap<>();
        for (PolicyExtractTarget target : targets) {
            String key = nullToEmpty(target.getRcCode()) + "|" + nullToEmpty(target.getRcRfileNo());
            distinct.putIfAbsent(key, target);
        }
        return new ArrayList<>(distinct.values());
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

    private String firstToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        int blankIndex = trimmed.indexOf(' ');
        return blankIndex < 0 ? trimmed : trimmed.substring(0, blankIndex);
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

    private record DepartmentLlmResult(PolicyExtractResponse policy, LlmPolicyMappingResult mappingResult) {
    }
}
