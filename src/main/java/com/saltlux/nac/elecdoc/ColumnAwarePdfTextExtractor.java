package com.saltlux.nac.elecdoc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class ColumnAwarePdfTextExtractor extends PDFTextStripper {

    private static final float SAME_LINE_Y_TOLERANCE = 3.0F;
    private static final float MIN_COLUMN_GAP_RATIO = 0.12F;
    private static final int MIN_LINES_PER_COLUMN = 3;

    private final StringBuilder documentText = new StringBuilder();
    private final List<LineText> pageLines = new ArrayList<>();
    private float pageWidth;

    public ColumnAwarePdfTextExtractor() throws IOException {
        super();
        setSortByPosition(true);
    }

    public String extract(PDDocument document) throws IOException {
        documentText.setLength(0);
        setStartPage(1);
        setEndPage(document.getNumberOfPages());
        super.getText(document);
        return documentText.toString();
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        pageLines.clear();
        pageWidth = page.getMediaBox().getWidth();
        super.startPage(page);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (text == null || text.isBlank() || textPositions == null || textPositions.isEmpty()) {
            return;
        }

        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;

        for (TextPosition position : textPositions) {
            minX = Math.min(minX, position.getXDirAdj());
            maxX = Math.max(maxX, position.getXDirAdj() + position.getWidthDirAdj());
            minY = Math.min(minY, position.getYDirAdj());
            maxY = Math.max(maxY, position.getYDirAdj() + position.getHeightDir());
        }

        pageLines.add(new LineText(text.trim(), minX, maxX, minY, maxY));
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        appendPageText();
        super.endPage(page);
    }

    private void appendPageText() {
        if (pageLines.isEmpty()) {
            return;
        }

        List<LineText> mergedLines = mergeSameYLines(pageLines);
        List<LineText> orderedLines = orderByDetectedColumns(mergedLines);

        for (LineText line : orderedLines) {
            if (!line.text().isBlank()) {
                documentText.append(line.text()).append('\n');
            }
        }
        documentText.append('\n');
    }

    private List<LineText> mergeSameYLines(List<LineText> sourceLines) {
        List<LineText> sorted = sourceLines.stream()
                .sorted(Comparator.comparing(LineText::minY).thenComparing(LineText::minX))
                .toList();

        List<LineText> result = new ArrayList<>();
        List<LineText> currentGroup = new ArrayList<>();
        Float currentY = null;

        for (LineText line : sorted) {
            if (currentY == null || Math.abs(line.minY() - currentY) <= SAME_LINE_Y_TOLERANCE) {
                currentGroup.add(line);
                currentY = currentY == null ? line.minY() : Math.min(currentY, line.minY());
            } else {
                result.addAll(splitOrMergeLineGroup(currentGroup));
                currentGroup.clear();
                currentGroup.add(line);
                currentY = line.minY();
            }
        }

        if (!currentGroup.isEmpty()) {
            result.addAll(splitOrMergeLineGroup(currentGroup));
        }

        return result;
    }

    private List<LineText> splitOrMergeLineGroup(List<LineText> group) {
        if (group.size() <= 1) {
            return List.copyOf(group);
        }

        List<LineText> sorted = group.stream()
                .sorted(Comparator.comparing(LineText::minX))
                .toList();

        List<LineText> result = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        float minX = sorted.get(0).minX();
        float maxX = sorted.get(0).maxX();
        float minY = sorted.get(0).minY();
        float maxY = sorted.get(0).maxY();

        for (int i = 0; i < sorted.size(); i++) {
            LineText line = sorted.get(i);
            if (i == 0) {
                text.append(line.text());
                continue;
            }

            float gap = line.minX() - maxX;
            boolean likelyDifferentColumn = gap > pageWidth * MIN_COLUMN_GAP_RATIO;
            if (likelyDifferentColumn) {
                result.add(new LineText(text.toString(), minX, maxX, minY, maxY));
                text = new StringBuilder(line.text());
                minX = line.minX();
                maxX = line.maxX();
                minY = line.minY();
                maxY = line.maxY();
            } else {
                text.append(' ').append(line.text());
                maxX = Math.max(maxX, line.maxX());
                maxY = Math.max(maxY, line.maxY());
            }
        }

        result.add(new LineText(text.toString(), minX, maxX, minY, maxY));
        return result;
    }

    private List<LineText> orderByDetectedColumns(List<LineText> lines) {
        float centerX = pageWidth / 2.0F;

        List<LineText> left = new ArrayList<>();
        List<LineText> right = new ArrayList<>();
        List<LineText> fullWidth = new ArrayList<>();

        for (LineText line : lines) {
            boolean isLeft = line.maxX() < centerX;
            boolean isRight = line.minX() > centerX;

            if (isLeft) {
                left.add(line);
            } else if (isRight) {
                right.add(line);
            } else {
                fullWidth.add(line);
            }
        }

        boolean twoColumnPage = left.size() >= MIN_LINES_PER_COLUMN && right.size() >= MIN_LINES_PER_COLUMN;
        if (!twoColumnPage) {
            return lines.stream()
                    .sorted(Comparator.comparing(LineText::minY).thenComparing(LineText::minX))
                    .toList();
        }

        left.sort(Comparator.comparing(LineText::minY).thenComparing(LineText::minX));
        right.sort(Comparator.comparing(LineText::minY).thenComparing(LineText::minX));
        fullWidth.sort(Comparator.comparing(LineText::minY).thenComparing(LineText::minX));

        float bodyStartY = firstBodyY(left, right);

        List<LineText> ordered = new ArrayList<>();
        ordered.addAll(fullWidth.stream().filter(line -> line.minY() < bodyStartY).toList());
        ordered.addAll(left);
        ordered.addAll(right);
        ordered.addAll(fullWidth.stream().filter(line -> line.minY() >= bodyStartY).toList());
        return ordered;
    }

    private float firstBodyY(List<LineText> left, List<LineText> right) {
        float leftY = left.isEmpty() ? Float.MAX_VALUE : left.get(0).minY();
        float rightY = right.isEmpty() ? Float.MAX_VALUE : right.get(0).minY();
        return Math.min(leftY, rightY);
    }

    private record LineText(String text, float minX, float maxX, float minY, float maxY) {
    }
}
