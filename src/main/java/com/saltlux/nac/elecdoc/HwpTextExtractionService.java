package com.saltlux.nac.elecdoc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bindata.EmbeddedBinaryData;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.gso.ControlPicture;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
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
        ExtractionCursor cursor = new ExtractionCursor(imageTagResult.imageTags());
        String contents = extractTextWithImageTags(hwpFile, cursor);
        boolean hasContents = contents != null && !contents.isBlank();

        return new TextExtractionResult(contents, "application/x-hwp", hasContents, imageTagResult.imgDatasJson());
    }

    private String extractTextWithImageTags(HWPFile hwpFile, ExtractionCursor cursor) throws Exception {
        StringBuilder sb = new StringBuilder();

        if (hwpFile.getBodyText() == null || hwpFile.getBodyText().getSectionList() == null) {
            return "";
        }

        for (Section section : hwpFile.getBodyText().getSectionList()) {
            if (section == null || section.getParagraphs() == null) {
                continue;
            }

            for (Paragraph paragraph : section.getParagraphs()) {
                appendParagraph(sb, paragraph, cursor);
            }
        }

        appendRemainingImageTags(sb, cursor);
        return sb.toString();
    }

    private void appendParagraph(StringBuilder sb, Paragraph paragraph, ExtractionCursor cursor) throws Exception {
        if (paragraph == null) {
            return;
        }

        String text = paragraph.getNormalString();
        if (text != null && !text.isBlank()) {
            appendLine(sb, text);
        }

        appendControls(sb, paragraph, cursor);
    }

    private void appendControls(StringBuilder sb, Paragraph paragraph, ExtractionCursor cursor) throws Exception {
        if (paragraph.getControlList() == null || paragraph.getControlList().isEmpty()) {
            return;
        }

        for (Control control : paragraph.getControlList()) {
            if (control instanceof ControlPicture) {
                String imageTag = cursor.nextImageTag();
                if (imageTag != null) {
                    appendLine(sb, imageTag);
                }
            } else if (control instanceof ControlTable table) {
                appendTable(sb, table, cursor);
            }
        }
    }

    private void appendTable(StringBuilder sb, ControlTable table, ExtractionCursor cursor) throws Exception {
        if (table.getRowList() == null || table.getRowList().isEmpty()) {
            return;
        }

        for (Row row : table.getRowList()) {
            if (row == null || row.getCellList() == null) {
                continue;
            }

            StringBuilder rowText = new StringBuilder();
            for (Cell cell : row.getCellList()) {
                if (cell == null || cell.getParagraphList() == null || cell.getParagraphList().getParagraphs() == null) {
                    continue;
                }

                StringBuilder cellText = new StringBuilder();
                for (Paragraph cellParagraph : cell.getParagraphList().getParagraphs()) {
                    String before = sb.toString();
                    StringBuilder temp = new StringBuilder();
                    appendParagraph(temp, cellParagraph, cursor);
                    String value = temp.toString().trim();
                    if (!value.isBlank()) {
                        if (!cellText.isEmpty()) {
                            cellText.append(' ');
                        }
                        cellText.append(value.replace('\n', ' ').trim());
                    }
                }

                if (!cellText.isEmpty()) {
                    if (!rowText.isEmpty()) {
                        rowText.append(" | ");
                    }
                    rowText.append(cellText);
                }
            }

            if (!rowText.isEmpty()) {
                appendLine(sb, rowText.toString());
            }
        }
    }

    private void appendRemainingImageTags(StringBuilder sb, ExtractionCursor cursor) {
        String imageTag;
        while ((imageTag = cursor.nextImageTag()) != null) {
            appendLine(sb, imageTag);
        }
    }

    private void appendLine(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append(value.trim()).append('\n');
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

    private static class ExtractionCursor {
        private final List<String> imageTags;
        private int imageIndex;

        private ExtractionCursor(List<String> imageTags) {
            this.imageTags = imageTags == null ? List.of() : imageTags;
            this.imageIndex = 0;
        }

        private String nextImageTag() {
            if (imageIndex >= imageTags.size()) {
                return null;
            }
            return imageTags.get(imageIndex++);
        }
    }
}
