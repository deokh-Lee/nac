package com.saltlux.nac.elecdoc;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.object.content.context_hpf.ManifestItem;
import kr.dogfoot.hwpxlib.reader.HWPXReader;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor;
import kr.dogfoot.hwpxlib.tool.textextractor.TextMarks;
import org.springframework.stereotype.Service;

@Service
public class HwpxTextExtractionService {

    private final ImageOutputService imageOutputService;

    public HwpxTextExtractionService(ImageOutputService imageOutputService) {
        this.imageOutputService = imageOutputService;
    }

    public TextExtractionResult extract(Path path, DocumentImageContext imageContext) throws Exception {
        HWPXFile hwpxFile = HWPXReader.fromFile(path.toFile());

        TextMarks textMarks = new TextMarks()
                .paraSeparatorAnd("\n")
                .lineBreakAnd("\n")
                .tabAnd("\t")
                .tableCellSeparatorAnd("\t")
                .tableRowSeparatorAnd("\n");

        String hwpxText = TextExtractor.extract(
                hwpxFile,
                TextExtractMethod.InsertControlTextBetweenParagraphText,
                false,
                textMarks
        );

        ImageExtractionResult imageResult = extractImages(hwpxFile, imageContext);
        String contents = appendImageTags(hwpxText, imageResult.tagText());
        boolean hasContents = contents != null && !contents.isBlank();

        return new TextExtractionResult(contents, "application/x-hwpx", hasContents, imageResult.imgDatasJson());
    }

    private ImageExtractionResult extractImages(HWPXFile hwpxFile, DocumentImageContext imageContext) throws Exception {
        if (hwpxFile.contentHPFFile() == null || hwpxFile.contentHPFFile().manifest() == null) {
            return ImageExtractionResult.empty();
        }

        LinkedHashMap<String, String> imagePaths = new LinkedHashMap<>();
        int imageSeq = 0;

        for (ManifestItem item : hwpxFile.contentHPFFile().manifest().items()) {
            if (item == null || item.mediaType() == null || !item.mediaType().startsWith("image/")) {
                continue;
            }
            if (item.attachedFile() == null || item.attachedFile().data() == null || item.attachedFile().data().length == 0) {
                continue;
            }

            int nextSeq = imageSeq + 1;
            String imagePath = imageOutputService.saveAsPng(imageContext, nextSeq, item.attachedFile().data());
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
