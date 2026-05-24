package com.saltlux.nac.elecdoc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OfficialDocumentPostProcessor {
    private static final Pattern N_FILE = Pattern.compile(".*_[Nn]\\d{2}\\..*");
    private static final Pattern TITLE_LINE = Pattern.compile("(?m)^\\s*\\uC81C\\s*\\uBAA9\\s*[|:]?\\s*(.+)$");
    private static final Pattern TITLE_PIPE = Pattern.compile("(?:^|\\|)\\s*\\uC81C\\s*\\uBAA9\\s*\\|\\s*(.+?)(?=\\s*(?:\\||\\n)\\s*(?:1\\s*[.]|\\uC2DC\\s*\\uD589|\\uC811\\s*\\uC218|\\uC6B0\\s*\\||\\uC804\\uD654|\\uD329\\uC2A4|$))");
    private static final Pattern TITLE_MARKER = Pattern.compile("\\uC81C\\s*\\uBAA9\\s*\\|\\s*.+?(?=\\n|$)");
    private static final Pattern IMPL_PIPE = Pattern.compile("\\uC2DC\\s*\\uD589\\s*\\|\\s*([^|()]+?)\\s*\\|\\s*\\((\\d{4})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.]?\\s*\\)");
    private static final Pattern RECV_PIPE = Pattern.compile("\\uC811\\s*\\uC218\\s*\\|\\s*([^|()]+?)\\s*\\|\\s*\\((\\d{4})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.]?\\s*\\)");
    private static final Pattern IMPL_SPACE = Pattern.compile("\\uC2DC\\s*\\uD589\\s+([^\\s()|]+)\\s*(?:\\|\\s*)?\\((\\d{4})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})");
    private static final Pattern RECV_SPACE = Pattern.compile("\\uC811\\s*\\uC218\\s+([^\\s()|]+)\\s*(?:\\|\\s*)?\\((\\d{4})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})");
    private static final Pattern IMPL_NO_DATE_PIPE = Pattern.compile("\\uC2DC\\s*\\uD589\\s*\\|\\s*([^|()]+?)(?=\\s*\\|\\s*(?:\\uC811\\s*\\uC218|\\uC6B0|\\uC804\\uD654|\\uC804\\uC1A1|\\uD329\\uC2A4|/|$))");
    private static final Pattern RECV_NO_DATE_PIPE = Pattern.compile("\\uC811\\s*\\uC218\\s*\\|\\s*([^|()]+?)(?=\\s*\\|\\s*(?:\\uC6B0|\\uC804\\uD654|\\uC804\\uC1A1|\\uD329\\uC2A4|/|$))");
    private static final Pattern BODY_START_NUMBERED = Pattern.compile("(?m)^\\s*1\\s*[.]\\s+.*$");
    private static final Pattern BODY_END = Pattern.compile("\\uB05D\\s*\\.");

    public void apply(ExtractElecDoc extract) {
        if (extract == null || !StringUtils.hasText(extract.getFileName()) || !N_FILE.matcher(extract.getFileName()).matches()) return;
        if (!StringUtils.hasText(extract.getContents())) return;
        String text = normalize(extract.getContents());
        extract.setHwpSubTitle(matchTitle(text));
        applyDocNoDate(text, IMPL_PIPE, IMPL_SPACE, IMPL_NO_DATE_PIPE, true, extract);
        applyDocNoDate(text, RECV_PIPE, RECV_SPACE, RECV_NO_DATE_PIPE, false, extract);
        String body = matchBody(text);
        if (StringUtils.hasText(body)) extract.setIndexingContents(body);
    }

    private String matchTitle(String text) {
        Matcher pipe = TITLE_PIPE.matcher(text);
        if (pipe.find()) return clean(pipe.group(1));
        Matcher line = TITLE_LINE.matcher(text);
        return line.find() ? clean(line.group(1)) : null;
    }

    private void applyDocNoDate(String text, Pattern primary, Pattern fallback, Pattern noDateFallback, boolean implementation, ExtractElecDoc extract) {
        Matcher m = primary.matcher(text);
        if (!m.find()) {
            m = fallback.matcher(text);
        }

        if (m.find()) {
            String no = clean(m.group(1));
            String date = String.format("%04d-%02d-%02d", Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            applyDocNoDateValue(implementation, extract, no, date);
            return;
        }

        Matcher noDateMatcher = noDateFallback.matcher(text);
        if (noDateMatcher.find()) {
            String no = clean(noDateMatcher.group(1));
            if (isValidDocNo(no)) {
                applyDocNoDateValue(implementation, extract, no, null);
            }
        }
    }

    private void applyDocNoDateValue(boolean implementation, ExtractElecDoc extract, String no, String date) {
        if (implementation) {
            extract.setImplementOrg(no);
            extract.setImplementDate(date);
        } else {
            extract.setReceiptOrg(no);
            extract.setReceiptDate(date);
        }
    }

    private boolean isValidDocNo(String value) {
        if (!StringUtils.hasText(value)) return false;
        String cleaned = value.trim();
        if (cleaned.equals("우") || cleaned.startsWith("우 ") || cleaned.startsWith("전화") || cleaned.startsWith("전송") || cleaned.startsWith("팩스")) return false;
        return cleaned.matches(".*-\\d+.*");
    }

    private String matchBody(String text) {
        String byTitle = matchBodyAfterTitle(text);
        if (StringUtils.hasText(byTitle)) return byTitle;

        Matcher s = BODY_START_NUMBERED.matcher(text);
        if (!s.find()) return null;
        return substringUntilEnd(text, s.start());
    }

    private String matchBodyAfterTitle(String text) {
        Matcher title = TITLE_MARKER.matcher(text);
        if (!title.find()) return null;
        int start = title.end();
        return substringUntilEnd(text, start);
    }

    private String substringUntilEnd(String text, int start) {
        Matcher e = BODY_END.matcher(text);
        int end = text.length();
        if (e.find(start)) end = e.end();
        String body = text.substring(start, end);
        return cleanBody(body);
    }

    private String cleanBody(String value) {
        if (value == null) return null;
        String cleaned = value
                .replaceAll("(?m)^\\s*[|]+\\s*", "")
                .replaceAll("(?m)^\\s*(수신자|경유|제목)\\s*[|:].*$", "")
                .replaceAll("(?m)^\\s*(행정안전부장관|교육과학기술부장관|.*장관)\\s*$", "")
                .replaceAll("(?m)^\\s*(주무관|행정사무관|.*과장|협조자|시행|접수|우|전화|전송|팩스|비공개)\\b.*$", "")
                .replaceAll("\\s*<img\\d+/>\\s*", " ")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').replaceAll("\\s*\\|\\s*", " | ");
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replace("|", " ").replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
