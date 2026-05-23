package com.saltlux.nac.elecdoc;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bindata.EmbeddedBinaryData;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractOption;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.springframework.stereotype.Service;

@Service
public class HwpTextExtractionService {

    private final ImageOutputService imageOutputService;

    public HwpTextExtractionService(ImageOutputService imageOutputService) {
        this.imageOutputService = imageOutputService;
    }

    public TextExtractionResult extract(Path path, DocumentImageContext imageContext) throws Exception {
        HWPFile hwpFile = HWPReader.fromFile(path.toFile());

        TextExtractOption option = new TextExtractOption();
        option.setMethod(TextExtractMethod.InsertControlTextBetweenParagraphText);
        option.setWithControlChar(false);
        option.setAppendEndingLF(true);

        String hwpText = TextExtractor.extract(hwpFile, option);
        ImageExtractionResult imageResult = extractImages(hwpFile, imageContext);
        String contents = appendImageTags(hwpText, imageResult.tagText());
        boolean hasContents = contents != null && !contents.isBlank();

        return new TextExtractionResult(contents, "application/x-hwp", hasContents, imageResult.imgDatasJson());
    }

    private ImageExtractionResult extractImages(HWPFile hwpFile, DocumentImageContext imageContext) throws Exception {
        if (hwpFile.getBinData() == null || hwpFile.getBinData().getEmbeddedBinaryDataList() == null) {
            return ImageExtractionResult.empty();
        }

        LinkedHashMap<String, String> imagePaths = new LinkedHashMap<>();
        int imageSeq = 0;

        for (EmbeddedBinaryData binaryData : hwpFile.getBinData().getEmbeddedBinaryDataList()) {
            if (binaryData == null || binaryData.getData() == null || binaryData.getData().length == 0) {
                continue;
            }

            int nextSeq = imageSeq + 1;
            String imagePath = imageOutputService.saveAsPng(imageContext, nextSeq, binaryData.getData());
            if (imagePath == null) {
                continue;
            }

            imageSeq = nextSeq;
            imagePaths.put("img" + imageSeq, imagePath);
        }

        return imageOutputService.toImageExtractionResult(imagePaths);
    }

    private String appendImageTags(String contents, String tagText) {
        String base = contents == null ? "" : contents;
        if (tagText == null || tagText.isBlank()) {
            return base;
        }
        return base + "\n" + tagText;
    }
}
