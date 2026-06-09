package com.saltlux.nac.chunk;

import com.saltlux.nac.elecdoc.FileTypeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RecordContentChunker {

    private static final int DEFAULT_MAX_CHARS = 3_500;
    private static final int DEFAULT_OVERLAP_CHARS = 350;
    private static final int MIN_CHUNK_CHARS = 120;

    private static final Pattern LAW_CHAPTER_PATTERN = Pattern.compile("^(제\\s*\\d+\\s*장)\\s*(.*)$");
    private static final Pattern LAW_SECTION_PATTERN = Pattern.compile("^(제\\s*\\d+\\s*절)\\s*(.*)$");
    private static final Pattern LAW_ARTICLE_PATTERN = Pattern.compile("^(제\\s*\\d+\\s*조(?:\\s*의\\s*\\d+)?)\\s*(?:\\(([^)]*)\\))?\\s*(.*)$");
    private static final Pattern LAW_HEADING_PATTERN = Pattern.compile("^(제\\s*\\d+\\s*(장|절|관)\\s*.*|제\\s*\\d+\\s*조(?:\\s*의\\s*\\d+)?\\s*(\\([^)]*\\))?.*)$", Pattern.MULTILINE);
    private static final Pattern STRUCTURED_HEADING_PATTERN = Pattern.compile("^((Ⅰ|Ⅱ|Ⅲ|Ⅳ|Ⅴ|Ⅵ|Ⅶ|Ⅷ|Ⅸ|Ⅹ)\\.|[0-9]+(\\.[0-9]+)*\\.|[가-하]\\.|[A-Z]\\.)\\s+.+$", Pattern.MULTILINE);
    private static final Pattern TOC_TITLE_PATTERN = Pattern.compile("^(목\\s*차|차\\s*례|CONTENTS?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOC_LINE_PATTERN = Pattern.compile(".*(\\.{2,}|…{2,}|\\s{3,})(\\d+|[ivxlcdm]+)\\s*$", Pattern.CASE_INSENSITIVE);

    public List<RecordContentChunk> chunk(ChunkSourceDocument source, ChunkOptions options) {
        ChunkOptions resolvedOptions = options == null ? new ChunkOptions(DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS) : options;
        String normalized = normalize(source.getIndexingContents());
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        String docRole = detectDocumentRole(source);
        String docType = detectDocumentType(source, normalized, docRole);
        List<TextBlock> blocks = splitByDocumentType(normalized, docType, resolvedOptions);
        List<RecordContentChunk> chunks = new ArrayList<>();
        int seq = 1;
        boolean hasContentChunk = false;

        for (TextBlock block : blocks) {
            for (TextBlock part : splitOversizedBlock(block, resolvedOptions)) {
                if (isNonContentFrontMatter(part, !hasContentChunk)) {
                    continue;
                }
                String text = part.text().trim();
                if (text.length() < MIN_CHUNK_CHARS && !chunks.isEmpty()) {
                    mergeIntoPrevious(chunks.get(chunks.size() - 1), text);
                    continue;
                }
                chunks.add(toChunk(source, seq++, docRole, docType, part));
                hasContentChunk = true;
            }
        }
        if (chunks.isEmpty() && shouldFallbackToContent(normalized)) {
            for (TextBlock block : splitParagraphDocument(normalized, resolvedOptions)) {
                for (TextBlock part : splitOversizedBlock(block, resolvedOptions)) {
                    chunks.add(toChunk(source, seq++, docRole, docType, part));
                }
            }
        }
        return chunks;
    }

    private List<TextBlock> splitByDocumentType(String text, String docType, ChunkOptions options) {
        if ("DRAFT".equals(docType)) {
            return splitDraft(text);
        }
        if ("LAW".equals(docType)) {
            return splitLaw(text, options);
        }
        if ("STRUCTURED".equals(docType)) {
            return splitByHeadings(text, STRUCTURED_HEADING_PATTERN, options);
        }
        return splitParagraphDocument(text, options);
    }

    private List<TextBlock> splitDraft(String text) {
        List<TextBlock> blocks = new ArrayList<>();
        int attachmentIndex = indexOfAnyLine(text, "붙임", "첨부");
        if (attachmentIndex > 0) {
            blocks.add(new TextBlock(null, text.substring(0, attachmentIndex).trim(), 0, attachmentIndex, null, null, null, null, null));
            blocks.add(new TextBlock("붙임목록", text.substring(attachmentIndex).trim(), attachmentIndex, text.length(), null, null, null, null, null));
            return blocks;
        }
        blocks.add(new TextBlock(null, text, 0, text.length(), null, null, null, null, null));
        return blocks;
    }

    private List<TextBlock> splitLaw(String text, ChunkOptions options) {
        String[] lines = text.split("\\n");
        List<TextBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentChapter = null;
        String currentSection = null;
        String articleNo = null;
        String articleTitle = null;
        String articleLead = null;
        int currentStart = 0;
        int cursor = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher chapterMatcher = LAW_CHAPTER_PATTERN.matcher(trimmed);
            Matcher sectionMatcher = LAW_SECTION_PATTERN.matcher(trimmed);
            Matcher articleMatcher = LAW_ARTICLE_PATTERN.matcher(trimmed);

            if (chapterMatcher.matches()) {
                if (current.length() > 0) {
                    blocks.add(lawBlock(current, currentStart, cursor, currentChapter, currentSection, articleNo, articleTitle, articleLead));
                    current.setLength(0);
                }
                currentChapter = normalizeHeading(chapterMatcher.group(1), chapterMatcher.group(2));
                currentSection = null;
                articleNo = null;
                articleTitle = null;
                articleLead = null;
                currentStart = cursor;
                current.append(line).append('\n');
            } else if (sectionMatcher.matches()) {
                if (current.length() > 0) {
                    blocks.add(lawBlock(current, currentStart, cursor, currentChapter, currentSection, articleNo, articleTitle, articleLead));
                    current.setLength(0);
                }
                currentSection = normalizeHeading(sectionMatcher.group(1), sectionMatcher.group(2));
                articleNo = null;
                articleTitle = null;
                articleLead = null;
                currentStart = cursor;
                current.append(line).append('\n');
            } else if (articleMatcher.matches()) {
                if (current.length() > 0) {
                    blocks.add(lawBlock(current, currentStart, cursor, currentChapter, currentSection, articleNo, articleTitle, articleLead));
                    current.setLength(0);
                }
                articleNo = compactSpaces(articleMatcher.group(1));
                articleTitle = nullToBlank(articleMatcher.group(2));
                articleLead = nullToBlank(articleMatcher.group(3));
                currentStart = cursor;
                current.append(line).append('\n');
            } else {
                if (current.length() == 0) {
                    currentStart = cursor;
                }
                current.append(line).append('\n');
            }
            cursor += line.length() + 1;
        }

        if (current.length() > 0) {
            blocks.add(lawBlock(current, currentStart, text.length(), currentChapter, currentSection, articleNo, articleTitle, articleLead));
        }
        return blocks.isEmpty() ? splitParagraphDocument(text, options) : blocks;
    }

    private TextBlock lawBlock(StringBuilder text,
                               int startOffset,
                               int endOffset,
                               String chapter,
                               String section,
                               String articleNo,
                               String articleTitle,
                               String articleLead) {
        String sectionPath = buildLawPath(chapter, section, articleNo);
        String chunkText = text.toString().trim();
        return new TextBlock(sectionPath, chunkText, startOffset, endOffset, chapter, section, articleNo, blankToNull(articleTitle), lawContent(chunkText, articleNo, articleTitle, articleLead));
    }

    private String buildLawPath(String chapter, String section, String articleNo) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(chapter)) {
            parts.add(chapter);
        }
        if (StringUtils.hasText(section)) {
            parts.add(section);
        }
        if (StringUtils.hasText(articleNo)) {
            parts.add(articleNo);
        }
        return String.join(" > ", parts);
    }

    private String lawContent(String chunkText, String articleNo, String articleTitle, String articleLead) {
        if (!StringUtils.hasText(articleNo)) {
            return null;
        }
        String firstLineContent = nullToBlank(articleLead);
        String headingRegex = "^" + Pattern.quote(articleNo) + "\\s*(?:\\(" + Pattern.quote(nullToBlank(articleTitle)) + "\\))?\\s*";
        String stripped = chunkText.replaceFirst(headingRegex, "").trim();
        if (StringUtils.hasText(firstLineContent) && stripped.startsWith(firstLineContent)) {
            return stripped;
        }
        return StringUtils.hasText(stripped) ? stripped : firstLineContent;
    }

    private List<TextBlock> splitByHeadings(String text, Pattern headingPattern, ChunkOptions options) {
        String[] lines = text.split("\\n");
        List<TextBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentPath = "";
        int currentStart = 0;
        int cursor = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            boolean heading = headingPattern.matcher(trimmed).matches();
            if (heading && current.length() > 0) {
                blocks.add(new TextBlock(currentPath, current.toString().trim(), currentStart, cursor, null, null, null, null, null));
                current.setLength(0);
                currentStart = cursor;
            }
            if (heading) {
                currentPath = trimmed;
            }
            current.append(line).append('\n');
            cursor += line.length() + 1;
        }

        if (current.length() > 0) {
            blocks.add(new TextBlock(currentPath, current.toString().trim(), currentStart, text.length(), null, null, null, null, null));
        }
        return blocks.isEmpty() ? splitParagraphDocument(text, options) : blocks;
    }

    private List<TextBlock> splitParagraphDocument(String text, ChunkOptions options) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<TextBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentStart = 0;
        int cursor = 0;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!StringUtils.hasText(trimmed)) {
                cursor += paragraph.length() + 2;
                continue;
            }
            if (current.length() > 0 && current.length() + trimmed.length() > options.chunkSize()) {
                blocks.add(new TextBlock("", current.toString().trim(), currentStart, cursor, null, null, null, null, null));
                current.setLength(0);
                currentStart = cursor;
            }
            if (current.length() == 0) {
                currentStart = cursor;
            }
            current.append(trimmed).append("\n\n");
            cursor += paragraph.length() + 2;
        }

        if (current.length() > 0) {
            blocks.add(new TextBlock("", current.toString().trim(), currentStart, text.length(), null, null, null, null, null));
        }
        return blocks;
    }

    private List<TextBlock> splitOversizedBlock(TextBlock block, ChunkOptions options) {
        if (block.text().length() <= options.chunkSize()) {
            return List.of(block);
        }

        List<String> sentences = splitSentences(block.text(), options);
        List<TextBlock> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int localStart = 0;
        int cursor = 0;

        for (String sentence : sentences) {
            if (current.length() > 0 && current.length() + sentence.length() > options.chunkSize()) {
                String chunkText = current.toString().trim();
                parts.add(block.withText(chunkText, block.startOffset() + localStart, block.startOffset() + cursor));
                String overlap = tail(chunkText, options.overlapSize());
                current.setLength(0);
                current.append(overlap);
                localStart = Math.max(0, cursor - overlap.length());
            }
            current.append(sentence).append(' ');
            cursor += sentence.length() + 1;
        }

        if (current.length() > 0) {
            parts.add(block.withText(current.toString().trim(), block.startOffset() + localStart, block.endOffset()));
        }
        return parts;
    }

    private boolean isNonContentFrontMatter(TextBlock block, boolean beforeFirstContent) {
        String text = block.text().trim();
        if (!StringUtils.hasText(text)) {
            return true;
        }
        if (isTableOfContents(text)) {
            return true;
        }
        return beforeFirstContent && isCoverLike(text);
    }

    private boolean isTableOfContents(String text) {
        String[] lines = text.split("\\n");
        int nonEmptyLines = 0;
        int tocLines = 0;
        boolean hasTocTitle = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            nonEmptyLines++;
            if (TOC_TITLE_PATTERN.matcher(trimmed).matches()) {
                hasTocTitle = true;
                continue;
            }
            if (TOC_LINE_PATTERN.matcher(trimmed).matches()) {
                tocLines++;
            }
        }

        if (nonEmptyLines == 0) {
            return true;
        }
        if (hasTocTitle && nonEmptyLines <= 2) {
            return true;
        }
        if (hasTocTitle && tocLines >= 2) {
            return true;
        }
        return tocLines >= 4 && tocLines >= nonEmptyLines / 2;
    }

    private boolean isCoverLike(String text) {
        if (text.length() > 1_500) {
            return false;
        }
        String[] lines = text.split("\\n");
        int nonEmptyLines = 0;
        int metadataLines = 0;
        int sentenceLikeLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            nonEmptyLines++;
            if (trimmed.matches(".*(기관명|부서명|작성일|작성자|문서번호|등록번호|보고서|계획서|검토서|용역명|발주처).*")) {
                metadataLines++;
            }
            if (trimmed.matches(".*(다\\.|요\\.|함\\.|음\\.)$")) {
                sentenceLikeLines++;
            }
        }

        if (nonEmptyLines == 0) {
            return true;
        }
        if (nonEmptyLines <= 4 && sentenceLikeLines == 0) {
            return true;
        }
        return metadataLines >= 2 && sentenceLikeLines == 0;
    }

    private boolean shouldFallbackToContent(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (text.length() > 1_500) {
            return true;
        }
        int sentenceEndCount = countMatches(text, Pattern.compile("(다\\.|요\\.|함\\.|음\\.)"));
        return sentenceEndCount >= 3;
    }

    private List<String> splitSentences(String text, ChunkOptions options) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!isSentenceBoundary(text, i, ch)) {
                continue;
            }
            addSentenceSlices(sentences, text.substring(start, i + 1), options);
            start = i + 1;
        }
        if (start < text.length()) {
            addSentenceSlices(sentences, text.substring(start), options);
        }
        return sentences;
    }

    private boolean isSentenceBoundary(String text, int index, char ch) {
        if (ch == '!' || ch == '?') {
            return true;
        }
        if (ch != '.') {
            return false;
        }
        if (index == 0) {
            return true;
        }
        char previous = text.charAt(index - 1);
        return previous == '다' || previous == '요' || previous == '함' || previous == '음'
                || Character.isDigit(previous)
                || Character.isLetter(previous);
    }

    private void addSentenceSlices(List<String> sentences, String sentence, ChunkOptions options) {
        String trimmed = sentence.trim();
        if (!StringUtils.hasText(trimmed)) {
            return;
        }
        int start = 0;
        while (start < trimmed.length()) {
            int end = Math.min(trimmed.length(), start + options.chunkSize());
            sentences.add(trimmed.substring(start, end));
            start = end;
        }
    }

    private String detectDocumentRole(ChunkSourceDocument source) {
        String fileName = StringUtils.hasText(source.getZipEntryFileName()) ? source.getZipEntryFileName() : source.getFileName();
        String documentType = FileTypeUtils.documentTypeOf(fileName);
        return switch (documentType) {
            case "N" -> "DRAFT_BODY";
            case "S" -> "ATTACHMENT";
            default -> "UNKNOWN";
        };
    }

    private String detectDocumentType(ChunkSourceDocument source, String text, String docRole) {
        String sample = text.length() > 20_000 ? text.substring(0, 20_000) : text;
        int lawScore = countMatches(sample, Pattern.compile("제\\s*\\d+\\s*(장|절|관|조(?:\\s*의\\s*\\d+)?)"))
                + countMatches(sample, Pattern.compile("[①②③④⑤⑥⑦⑧⑨⑩]"));
        int structuredScore = countMatches(sample, STRUCTURED_HEADING_PATTERN);
        int draftScore = countDraftKeywords(sample);

        if ("DRAFT_BODY".equals(docRole) && draftScore >= 2) {
            return "DRAFT";
        }
        if (lawScore >= 5) {
            return "LAW";
        }
        if (structuredScore >= 4) {
            return "STRUCTURED";
        }
        if (looksTableLike(sample)) {
            return "TABLE";
        }
        return "GENERAL";
    }

    private int countDraftKeywords(String text) {
        int score = 0;
        for (String keyword : List.of("수신", "경유", "제목", "시행", "접수", "결재", "붙임")) {
            if (text.contains(keyword)) {
                score++;
            }
        }
        return score;
    }

    private boolean looksTableLike(String text) {
        String[] lines = text.split("\\n");
        if (lines.length < 5) {
            return false;
        }
        int tableLines = 0;
        for (String line : lines) {
            if (line.contains("|") || line.split("\\s{2,}").length >= 4) {
                tableLines++;
            }
        }
        return tableLines >= Math.max(5, lines.length / 3);
    }

    private int countMatches(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int indexOfAnyLine(String text, String... prefixes) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String prefix : prefixes) {
            int index = lower.indexOf(prefix.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    private String normalizeHeading(String no, String title) {
        String compactNo = compactSpaces(no);
        String compactTitle = compactSpaces(title);
        return StringUtils.hasText(compactTitle) ? compactNo + " " + compactTitle : compactNo;
    }

    private RecordContentChunk toChunk(ChunkSourceDocument source, int seq, String docRole, String docType, TextBlock block) {
        RecordContentChunk chunk = new RecordContentChunk();
        chunk.setExtractIdx(source.getExtractIdx());
        chunk.setRcRfileNo(source.getRcRfileNo());
        chunk.setRcRitemNo(source.getRcRitemNo());
        chunk.setFileName(source.getFileName());
        chunk.setZipEntryFileName(source.getZipEntryFileName());
        chunk.setZipSeq(source.getZipSeq());
        chunk.setChunkSeq(seq);
        chunk.setDocRole(docRole);
        chunk.setDocType(docType);
        chunk.setSectionPath(block.sectionPath());
        chunk.setLawChapter(block.lawChapter());
        chunk.setLawSection(block.lawSection());
        chunk.setLawArticleNo(block.lawArticleNo());
        chunk.setLawArticleTitle(block.lawArticleTitle());
        chunk.setLawArticleContent(block.lawArticleContent());
        chunk.setChunkText(block.text());
        chunk.setStartOffset(block.startOffset());
        chunk.setEndOffset(block.endOffset());
        chunk.setCharCount(chunk.getChunkText().length());
        chunk.setTokenCount(estimateTokenCount(chunk.getChunkText()));
        chunk.setChunkStatus("READY");
        return chunk;
    }

    private void mergeIntoPrevious(RecordContentChunk previous, String text) {
        previous.setChunkText(previous.getChunkText() + "\n\n" + text);
        previous.setCharCount(previous.getChunkText().length());
        previous.setTokenCount(estimateTokenCount(previous.getChunkText()));
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("(?m)[ ]{2,}$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String tail(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars);
    }

    private int estimateTokenCount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int koreanChars = 0;
        int asciiWords = 0;
        Matcher wordMatcher = Pattern.compile("[A-Za-z0-9_]+").matcher(text);
        while (wordMatcher.find()) {
            asciiWords++;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0xAC00 && ch <= 0xD7A3) {
                koreanChars++;
            }
        }
        return Math.max(1, asciiWords + (int) Math.ceil(koreanChars / 2.5));
    }

    private String compactSpaces(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record TextBlock(
            String sectionPath,
            String text,
            int startOffset,
            int endOffset,
            String lawChapter,
            String lawSection,
            String lawArticleNo,
            String lawArticleTitle,
            String lawArticleContent
    ) {
        TextBlock withText(String text, int startOffset, int endOffset) {
            return new TextBlock(sectionPath, text, startOffset, endOffset, lawChapter, lawSection, lawArticleNo, lawArticleTitle, text);
        }
    }
}
