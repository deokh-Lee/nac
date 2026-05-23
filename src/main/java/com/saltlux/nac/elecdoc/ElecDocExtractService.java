package com.saltlux.nac.elecdoc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ElecDocExtractService {

    private static final Logger log = LoggerFactory.getLogger(ElecDocExtractService.class);

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
        String targetYear = StringUtils.hasText(transferYear) ? transferYear : properties.getDefaultTransferYear();
        int targetLimit = limit == null || limit <= 0 ? properties.getBatchSize() : limit;
        int targetOffset = offset == null || offset < 0 ? 0 : offset;

        List<CnElecDoc> documents = elecDocMapper.findTargetDocuments(targetYear, targetLimit, targetOffset);

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

    @Transactional
    public void extractOne(CnElecDoc document) {
        String fileName = documentPathResolver.resolveFileName(document);
        Path filePath = documentPathResolver.resolve(document);

        ExtractElecDoc extract = new ExtractElecDoc();
        extract.setFileName(fileName);
        extract.setRcRfileNo(document.getRcRfileNo());
        extract.setRcRitemNo(document.getRcRitemNo());
        extract.setImgDatas("[]");
        extract.setFileGubun(FileTypeUtils.fileGubunOf(fileName));
        extract.setDataYear(parseYear(document));
        extract.setQueueState("C");

        try {
            TextExtractionResult result = textExtractionService.extract(filePath);
            extract.setContents(result.contents());
            extract.setIndexingContents(result.contents());
            extract.setFileType(resolveFileType(fileName, result.fileType()));
            extract.setHasContents(result.hasContents() ? "Y" : "N");
            extract.setExtractStatus("PASS");
            extract.setExtractErrMsg(null);
        } catch (Exception e) {
            extract.setContents(null);
            extract.setIndexingContents(null);
            extract.setFileType(FileTypeUtils.fileTypeOf(fileName));
            extract.setHasContents("N");
            extract.setExtractStatus("FAIL");
            extract.setExtractErrMsg(toStackTrace(e));
        }

        elecDocMapper.upsertExtractDocument(extract);
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

    private String resolveFileType(String fileName, String detectedType) {
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
