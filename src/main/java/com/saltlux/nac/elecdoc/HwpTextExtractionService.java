package com.saltlux.nac.elecdoc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bindata.EmbeddedBinaryData;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlPicture;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.reader.HWPReader;
import org.springframework.stereotype.Service;

@Service
public class HwpTextExtractionService {

    private final ImageOutputService imageOutputService;

    public HwpTextExtractionService(ImageOutputService imageOutputService) {
        this.imageOutputService = imageOutputService;
    }

    public TextExtractionResult extract(Path path, DocumentImageContext imageContext) throws Exception {
        HWPFile hwpFile = HWPReader.fromFile(path.toFile());

        HwpImageTagResult imageTagResult = extractImages(hwpFile, imageContext);
        String contents = extractTextWithImageTags(hwpFile, imageTagResult.imageTags());
        boolean hasContents = contents != null && !contents.isBlank();

        return new TextExtractionResult(contents, "application/x-hwp", hasContents, imageTagResult.imgDatasJson());
    }

    private String extractTextWithImageTags(HWPFile hwpFile, List<String> imageTags) throws Exception {
        StringBuilder sb = new StringBuilder();
        int imageIndex = 0;

        if (hwpFile.getBodyText() == null || hwpFile.getBodyText().getSectionList() == null) {
            return "";
        }

        for (Section section : hwpFile.getBodyText().getSectionList()) {
            if (section == null || section.getParagraphs() == null) {
                continue;
            }

            for (Paragraph paragraph : section.getParagraphs()) {
                if (paragraph == null) {
                    continue;
                }

                String text = paragraph.getNormalString();
                if (text != null && !text.isBlank()) {
                    sb.append(text);
                }

                int pictureCount = countPictureControls(paragraph);
                for (int i = 0; i < pictureCount && imageIndex < imageTags.size(); i++) {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                    sb.append(imageTags.get(imageIndex++)).append('\n');
                }

                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
            }
        }

        while (imageIndex < imageTags.size()) {
            sb.append(imageTags.get(imageIndex++)).append('\n');
        }

        return sb.toString();
    }

    private int countPictureControls(Paragraph paragraph) {
        if (paragraph.getControlList() == null || paragraph.getControlList().isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Control control : paragraph.getControlList()) {
            if (control instanceof ControlPicture) {
                count++;
            }
        }
        return count;
    }

    private HwpImageTagResult extractImages(HWPFile hwpFile, DocumentImageContext imageContext) throws Exception {
        if (hwpFile.getBinData() == null || hwpFile.getBinData().getEmbeddedBinaryDataList() == null) {
            return HwpImageTagResult.empty();
        }

        LinkedHashMap<String, String> imagePaths = new LinkedHashMap<>();
        List<String> imageTags = new ArrayList<>();
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
            String imageKey = "img" + imageSeq;
            imagePaths.put(imageKey, imagePath);
            imageTags.add("<" + imageKey + "/>");
        }

        return new HwpImageTagResult(imageTags, imageOutputService.toJson(imagePaths));
    }

    private record HwpImageTagResult(List<String> imageTags, String imgDatasJson) {
        static HwpImageTagResult empty() {
            return new HwpImageTagResult(List.of(), "[]");
        }
    }
}
