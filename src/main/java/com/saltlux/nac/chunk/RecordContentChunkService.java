package com.saltlux.nac.chunk;

import com.saltlux.nac.elecdoc.DocumentExtractProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RecordContentChunkService {

    private static final Logger log = LoggerFactory.getLogger(RecordContentChunkService.class);
    private static final int DEFAULT_CHUNK_SIZE = 3_500;
    private static final int DEFAULT_OVERLAP_SIZE = 350;

    private final RecordContentChunkMapper chunkMapper;
    private final RecordContentChunker chunker;
    private final DocumentExtractProperties properties;

    public RecordContentChunkService(RecordContentChunkMapper chunkMapper,
                                     RecordContentChunker chunker,
                                     DocumentExtractProperties properties) {
        this.chunkMapper = chunkMapper;
        this.chunker = chunker;
        this.properties = properties;
    }

    public ChunkBatchResult chunkBatch(String transferYear,
                                       Integer dataYear,
                                       Integer limit,
                                       Integer offset,
                                       Integer chunkSize,
                                       Integer overlapSize,
                                       boolean recreate) {
        String targetTransferYear = StringUtils.hasText(transferYear) ? transferYear : properties.getDefaultTransferYear();
        int targetLimit = limit == null || limit <= 0 ? properties.getBatchSize() : limit;
        int targetOffset = offset == null || offset < 0 ? 0 : offset;
        ChunkOptions options = resolveChunkOptions(chunkSize, overlapSize);

        List<ChunkSourceDocument> targets = chunkMapper.findChunkTargets(
                targetTransferYear,
                dataYear,
                targetLimit,
                targetOffset,
                recreate
        );

        BatchCounter counter = processTargets(targets, recreate, options);
        return new ChunkBatchResult(
                targetTransferYear,
                dataYear,
                targetLimit,
                targetOffset,
                recreate,
                options.chunkSize(),
                options.overlapSize(),
                targets.size(),
                counter.successCount(),
                counter.failCount(),
                counter.chunkCount()
        );
    }

    public ChunkAllBatchResult chunkAll(String transferYear,
                                        Integer dataYear,
                                        Integer limit,
                                        Integer maxLoop,
                                        Integer chunkSize,
                                        Integer overlapSize,
                                        boolean recreate) {
        String targetTransferYear = StringUtils.hasText(transferYear) ? transferYear : properties.getDefaultTransferYear();
        int batchSize = limit == null || limit <= 0 ? properties.getBatchSize() : limit;
        int loopLimit = maxLoop == null || maxLoop <= 0 ? 10_000 : maxLoop;
        ChunkOptions options = resolveChunkOptions(chunkSize, overlapSize);
        int loopCount = 0;
        int totalTargetCount = 0;
        int totalSuccessCount = 0;
        int totalFailCount = 0;
        int totalChunkCount = 0;
        boolean completed = false;

        while (loopCount < loopLimit) {
            int requestOffset = recreate ? loopCount * batchSize : 0;
            loopCount++;
            List<ChunkSourceDocument> targets = chunkMapper.findChunkTargets(
                    targetTransferYear,
                    dataYear,
                    batchSize,
                    requestOffset,
                    recreate
            );
            BatchCounter counter = processTargets(targets, recreate, options);
            totalTargetCount += targets.size();
            totalSuccessCount += counter.successCount();
            totalFailCount += counter.failCount();
            totalChunkCount += counter.chunkCount();

            log.info("record-content/chunks/all loop#{} | transferYear={} | dataYear={} | batchSize={} | offset={} | recreate={} | chunkSize={} | overlapSize={} | target={} | success={} | fail={} | chunks={}",
                    loopCount,
                    targetTransferYear,
                    dataYear,
                    batchSize,
                    requestOffset,
                    recreate,
                    options.chunkSize(),
                    options.overlapSize(),
                    targets.size(),
                    counter.successCount(),
                    counter.failCount(),
                    counter.chunkCount());

            if (targets.isEmpty()) {
                completed = true;
                break;
            }
        }

        return new ChunkAllBatchResult(
                targetTransferYear,
                dataYear,
                batchSize,
                loopLimit,
                recreate,
                options.chunkSize(),
                options.overlapSize(),
                loopCount,
                totalTargetCount,
                totalSuccessCount,
                totalFailCount,
                totalChunkCount,
                completed
        );
    }

    private BatchCounter processTargets(List<ChunkSourceDocument> targets, boolean recreate, ChunkOptions options) {
        if (targets == null || targets.isEmpty()) {
            return new BatchCounter(0, 0, 0);
        }

        int workerCount = Math.min(Math.max(1, properties.getThreadCount()), targets.size());
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        List<Future<BatchCounter>> futures = new ArrayList<>();

        log.info("record content chunk worker start | target={} | workerCount={} | recreate={} | chunkSize={} | overlapSize={}",
                targets.size(), workerCount, recreate, options.chunkSize(), options.overlapSize());

        for (int i = 0; i < workerCount; i++) {
            List<ChunkSourceDocument> workerTargets = new ArrayList<>();
            for (int j = i; j < targets.size(); j += workerCount) {
                workerTargets.add(targets.get(j));
            }
            futures.add(executorService.submit(createWorkerTask(i + 1, workerTargets, recreate, options)));
        }

        int successCount = 0;
        int failCount = 0;
        int chunkCount = 0;
        try {
            for (Future<BatchCounter> future : futures) {
                try {
                    BatchCounter counter = future.get();
                    successCount += counter.successCount();
                    failCount += counter.failCount();
                    chunkCount += counter.chunkCount();
                } catch (Exception e) {
                    failCount++;
                    log.warn("record content chunk worker future failed | error={}", safeMessage(e));
                }
            }
        } finally {
            executorService.shutdown();
        }

        log.info("record content chunk worker end | target={} | workerCount={} | success={} | fail={} | chunks={}",
                targets.size(), workerCount, successCount, failCount, chunkCount);

        return new BatchCounter(successCount, failCount, chunkCount);
    }

    private Callable<BatchCounter> createWorkerTask(int workerNo,
                                                    List<ChunkSourceDocument> targets,
                                                    boolean recreate,
                                                    ChunkOptions options) {
        return () -> {
            int successCount = 0;
            int failCount = 0;
            int chunkCount = 0;
            log.info("record content chunk worker assigned | workerNo={} | size={}", workerNo, targets.size());

            for (ChunkSourceDocument target : targets) {
                try {
                    int contentLength = target.getIndexingContents() == null ? 0 : target.getIndexingContents().length();
                    long startTime = System.currentTimeMillis();
                    log.info("record content chunk start | workerNo={} | extractIdx={} | rcRfileNo={} | rcRitemNo={} | fileName={} | zipSeq={} | contentLength={}",
                            workerNo,
                            target.getExtractIdx(),
                            target.getRcRfileNo(),
                            target.getRcRitemNo(),
                            target.getFileName(),
                            target.getZipSeq(),
                            contentLength);
                    int created = chunkOne(target, recreate, options);
                    successCount++;
                    chunkCount += created;
                    log.info("record content chunked | workerNo={} | extractIdx={} | rcRfileNo={} | rcRitemNo={} | fileName={} | zipSeq={} | chunks={} | elapsedMs={}",
                            workerNo,
                            target.getExtractIdx(),
                            target.getRcRfileNo(),
                            target.getRcRitemNo(),
                            target.getFileName(),
                            target.getZipSeq(),
                            created,
                            System.currentTimeMillis() - startTime);
                } catch (Exception e) {
                    failCount++;
                    log.warn("record content chunk failed | workerNo={} | extractIdx={} | rcRfileNo={} | rcRitemNo={} | fileName={} | zipSeq={} | error={}",
                            workerNo,
                            target.getExtractIdx(),
                            target.getRcRfileNo(),
                            target.getRcRitemNo(),
                            target.getFileName(),
                            target.getZipSeq(),
                            safeMessage(e));
                }
            }
            log.info("record content chunk worker finished | workerNo={} | success={} | fail={} | chunks={}",
                    workerNo, successCount, failCount, chunkCount);
            return new BatchCounter(successCount, failCount, chunkCount);
        };
    }

    @Transactional
    public int chunkOne(ChunkSourceDocument target, boolean recreate, ChunkOptions options) {
        List<RecordContentChunk> chunks = chunker.chunk(target, options);
        chunkMapper.deleteChunksForDocument(target);
        if (chunks.isEmpty()) {
            chunkMapper.insertChunk(createSkipChunk(target));
            return 0;
        }
        for (RecordContentChunk chunk : chunks) {
            chunkMapper.insertChunk(chunk);
        }
        return chunks.size();
    }

    private ChunkOptions resolveChunkOptions(Integer chunkSize, Integer overlapSize) {
        int resolvedChunkSize = chunkSize == null || chunkSize <= 0 ? DEFAULT_CHUNK_SIZE : chunkSize;
        int resolvedOverlapSize = overlapSize == null || overlapSize < 0 ? DEFAULT_OVERLAP_SIZE : overlapSize;
        if (resolvedOverlapSize >= resolvedChunkSize) {
            resolvedOverlapSize = Math.max(0, resolvedChunkSize / 10);
        }
        return new ChunkOptions(resolvedChunkSize, resolvedOverlapSize);
    }

    private RecordContentChunk createSkipChunk(ChunkSourceDocument target) {
        RecordContentChunk chunk = new RecordContentChunk();
        chunk.setExtractIdx(target.getExtractIdx());
        chunk.setRcRfileNo(target.getRcRfileNo());
        chunk.setRcRitemNo(target.getRcRitemNo());
        chunk.setFileName(target.getFileName());
        chunk.setZipEntryFileName(target.getZipEntryFileName());
        chunk.setZipSeq(target.getZipSeq());
        chunk.setChunkSeq(0);
        chunk.setDocRole("UNKNOWN");
        chunk.setDocType("SKIP");
        chunk.setSectionPath(null);
        chunk.setChunkText("[SKIPPED] no content chunk generated");
        chunk.setStartOffset(null);
        chunk.setEndOffset(null);
        chunk.setCharCount(chunk.getChunkText().length());
        chunk.setTokenCount(0);
        chunk.setChunkStatus("SKIP");
        return chunk;
    }

    private String safeMessage(Throwable e) {
        if (e == null) {
            return "";
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private record BatchCounter(int successCount, int failCount, int chunkCount) {
    }
}
