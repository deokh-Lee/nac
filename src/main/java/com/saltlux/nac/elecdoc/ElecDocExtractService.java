package com.saltlux.nac.elecdoc;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ElecDocExtractService {

    private static final Logger log = LoggerFactory.getLogger(ElecDocExtractService.class);
    private static final int NORMAL_FILE_ZIP_SEQ = 0;
    private static final Charset ZIP_ENTRY_CHARSET = Charset.forName("MS949");

    private final ElecDocMapper elecDocMapper;
    private final DocumentPathResolver documentPathResolver;
    private final DocumentExtractProperties properties;
    private final TextExtractionService textExtractionService;

    public ElecDocExtractService(ElecDocMapper elecDocMapper,
                                 DocumentPathResolver documentPathResolver,
                                 DocumentExtractProperties properties,
                                 TextExtractionService textExtractionService) {
        this.elecDocMapper = elecDocMapper;
        this.documentPathResolver = documentPathResolver;
        this.properties = properties;
        this.textExtractionService = textExtractionService;
    }

    public ExtractBatchResult extractBatch(String transferYear, Integer limit, Integer offset) {
        return extractBatch(transferYear, limit, offset, false);
    }

    public ExtractBatchResult extractBatch(String transferYear, Integer limit, Integer offset, boolean retryFail) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : properties.getDefaultTransferYear();
        int targetLimit = limit == null || limit <= 0 ? properties.getBatchSize() : limit;
        int targetOffset = offset == null || offset < 0 ? 0 : offset;

        List<CnElecDoc> documents = elecDocMapper.findTargetDocuments(targetYear, targetLimit, targetOffset, retryFail);

        int successCount = 0;
        int failCount = 0;

        for (CnElecDoc document : documents) {
            try {
                extractOne(document);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("Document extraction failed. transferYear={}, rcRfileNo={}, rcRitemNo={}, fileName={}",
                        document.getTransferYear(),
                        document.getRcRfileNo(),
                        document.getRcRitemNo(),
                        documentPathResolver.resolveFileName(document),
                        e);
            }
        }

        return new ExtractBatchResult(
                targetYear,
                targetLimit,
                targetOffset,
                documents.size(),
                successCount,
                failCount
        );
    }

    public ExtractAllBatchResult extractAll(String transferYear, Integer limit, Integer maxLoop, Boolean retryFail) {
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : properties.getDefaultTransferYear();
        int batchSize = limit == null || limit <= 0 ? properties.getBatchSize() : limit;
        int loopLimit = maxLoop == null || maxLoop <= 0 ? 10_000 : maxLoop;
        boolean shouldRetryFail = Boolean.TRUE.equals(retryFail);

        int loopCount = 0;
        int totalTargetCount = 0;
        int totalSuccessCount = 0;
        int totalFailCount = 0;
        boolean completed = false;

        while (loopCount < loopLimit) {
            loopCount++;

            ExtractBatchResult result = extractBatch(targetYear, batchSize, 0, shouldRetryFail);
            totalTargetCount += result.targetCount();
            totalSuccessCount += result.successCount();
            totalFailCount += result.failCount();

            log.info("extract/all loop#{} | year={} | batchSize={} | target={} | success={} | fail={} | totalTarget={} | totalSuccess={} | totalFail={}",
                    loopCount,
                    targetYear,
                    batchSize,
                    result.targetCount(),
                    result.successCount(),
                    result.failCount(),
                    totalTargetCount,
                    totalSuccessCount,
                    totalFailCount);

            if (result.targetCount() == 0) {
                completed = true;
                break;
            }
        }

        return new ExtractAllBatchResult(
                targetYear,
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

    @Transactional
    public void extractOne(CnElecDoc document) {
        String fileName = documentPathResolver.resolveFileName(document);
        Path filePath = documentPathResolver.resolve(document);

        if (isZipFile(fileName)) {
            extractZipEntries(document, filePath);
            return;
        }

        ExtractElecDoc extract = createBaseExtract(document, fileName, NORMAL_FILE_ZIP_SEQ);
        extractSingleFile(filePath, extract, fileName, FileTypeUtils.fileTypeOf(fileName), createImageContext(document, fileName));
        elecDocMapper.upsertExtractDocument(extract);
    }

    private void extractZipEntries(CnElecDoc document, Path zipFilePath) {
        if (!Files.exists(zipFilePath)) {
            ExtractElecDoc failExtract = createBaseExtract(document, documentPathResolver.resolveFileName(document), NORMAL_FILE_ZIP_SEQ);
            failExtract.setFileType("ZIP");
            failExtract.setFileGubun("ZIP");
            failExtract.setHasContents("N");
            failExtract.setExtractStatus("FAIL");
            failExtract.setExtractErrMsg("ZIP file not found: " + zipFilePath);
            elecDocMapper.upsertExtractDocument(failExtract);
            return;
        }

        int seq = 0;
        try (InputStream inputStream = Files.newInputStream(zipFilePath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream, ZIP_ENTRY_CHARSET)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                String entryFileName = extractEntryFileName(entry.getName());
                if (!StringUtils.hasText(entryFileName)) {
                    zipInputStream.closeEntry();
                    continue;
                }

                seq++;
                ExtractElecDoc extract = createBaseExtract(document, entryFileName, seq);
                extract.setFileType("ZIP");
                extract.setFileGubun(FileTypeUtils.fileGubunOf(entryFileName));

                Path tempFile = null;
                try {
                    tempFile = createTempFile(entryFileName);
                    Files.copy(zipInputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    extractSingleFile(tempFile, extract, entryFileName, "ZIP", createImageContext(document, entryFileName));
                } catch (Exception e) {
                    extract.setContents(null);
                    extract.setIndexingContents(null);
                    extract.setHasContents("N");
                    extract.setExtractStatus("FAIL");
                    extract.setExtractErrMsg(toStackTrace(e));
                } finally {
                    deleteQuietly(tempFile);
                    elecDocMapper.upsertExtractDocument(extract);
                    zipInputStream.closeEntry();
                }
            }

            if (seq == 0) {
                ExtractElecDoc emptyExtract = createBaseExtract(document, documentPathResolver.resolveFileName(document), NORMAL_FILE_ZIP_SEQ);
                emptyExtract.setFileType("ZIP");
                emptyExtract.setFileGubun("ZIP");
                emptyExtract.setHasContents("N");
                emptyExtract.setExtractStatus("FAIL");
                emptyExtract.setExtractErrMsg("ZIP has no file entries: " + zipFilePath);
                elecDocMapper.upsertExtractDocument(emptyExtract);
            }
        } catch (Exception e) {
            ExtractElecDoc failExtract = createBaseExtract(document, documentPathResolver.resolveFileName(document), NORMAL_FILE_ZIP_SEQ);
            failExtract.setFileType("ZIP");
            failExtract.setFileGubun("ZIP");
            failExtract.setHasContents("N");
            failExtract.setExtractStatus("FAIL");
            failExtract.setExtractErrMsg(toStackTrace(e));
            elecDocMapper.upsertExtractDocument(failExtract);
        }
    }

    private void extractSingleFile(Path filePath,
                                   ExtractElecDoc extract,
                                   String targetFileName,
                                   String forcedFileType,
                                   DocumentImageContext imageContext) {
        try {
            TextExtractionResult result = textExtractionService.extract(filePath, imageContext);
            extract.setContents(result.contents());
            extract.setIndexingContents(result.contents());
            extract.setImgDatas(StringUtils.hasText(result.imgDatas()) ? result.imgDatas() : "[]");
            extract.setFileType(resolveFileType(targetFileName, result.fileType(), forcedFileType));
            extract.setHasContents(result.hasContents() ? "Y" : "N");
            extract.setExtractStatus("PASS");
            extract.setExtractErrMsg(null);
        } catch (Exception e) {
            extract.setContents(null);
            extract.setIndexingContents(null);
            extract.setFileType(resolveFileType(targetFileName, null, forcedFileType));
            extract.setHasContents("N");
            extract.setExtractStatus("FAIL");
            extract.setExtractErrMsg(toStackTrace(e));
        }
    }

    private ExtractElecDoc createBaseExtract(CnElecDoc document, String fileName, Integer zipSeq) {
        ExtractElecDoc extract = new ExtractElecDoc();
        extract.setFileName(fileName);
        extract.setRcRfileNo(document.getRcRfileNo());
        extract.setRcRitemNo(document.getRcRitemNo());
        extract.setZipSeq(zipSeq == null ? NORMAL_FILE_ZIP_SEQ : zipSeq);
        extract.setImgDatas("[]");
        extract.setFileGubun(FileTypeUtils.fileGubunOf(fileName));
        extract.setDataYear(parseYear(document));
        extract.setQueueState("C");
        return extract;
    }

    private DocumentImageContext createImageContext(CnElecDoc document, String fileName) {
        String transferYear = StringUtils.hasText(document.getTransferYear())
                ? document.getTransferYear()
                : properties.getDefaultTransferYear();

        return new DocumentImageContext(
                transferYear,
                document.getRcRfileNo(),
                document.getRcRitemNo(),
                fileName
        );
    }

    private boolean isZipFile(String fileName) {
        return "zip".equals(FileTypeUtils.extensionOf(fileName).toLowerCase(Locale.ROOT));
    }

    private String extractEntryFileName(String entryName) {
        if (!StringUtils.hasText(entryName)) {
            return null;
        }
        String normalized = entryName.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            return normalized.substring(lastSlash + 1);
        }
        return normalized;
    }

    private Path createTempFile(String fileName) throws IOException {
        String extension = FileTypeUtils.extensionOf(fileName);
        String suffix = StringUtils.hasText(extension) ? "." + extension : ".tmp";
        return Files.createTempFile("nac-zip-entry-", suffix);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", path, e);
        }
    }

    private Integer parseYear(CnElecDoc document) {
        String year = StringUtils.hasText(document.getTransferYear())
                ? document.getTransferYear()
                : document.getProdYear();

        if (!StringUtils.hasText(year)) {
            return null;
        }

        try {
            return Integer.parseInt(year);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveFileType(String fileName, String detectedType, String forcedFileType) {
        if (StringUtils.hasText(forcedFileType)) {
            return forcedFileType;
        }
        String extensionType = FileTypeUtils.fileTypeOf(fileName);
        if (!"UNKNOWN".equals(extensionType)) {
            return extensionType;
        }
        return detectedType;
    }

    private String toStackTrace(Exception e) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }
}
