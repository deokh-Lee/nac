package com.saltlux.nac.elecdoc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OfficialDocumentPostProcessor {
    private static final Pattern N_FILE = Pattern.compile(".*_[Nn]\\d{2}\\..*");
    private static final Pattern TITLE = Pattern.compile("(?m)^\\s*\\uC81C\\s*\\uBAA9\\s*[|:]?\\s*(.+)$");
    private static final Pattern IMPL = Pattern.compile("\\uC2DC\\s*\\uD589\\s+([^\\s()]+)\\s*\\((\\d{4})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})");
    private static final Pattern RECV = Pattern.compile("\\uC811\\s*\\uC218\\s+([^\\s()]+)\\s*\\((\\d{4})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})");
    private static final Pattern BODY_START = Pattern.compile("(?m)^\\s*1\\s*[.]\\s+.*$");
    private static final Pattern BODY_END = Pattern.compile("\\uB05D\\s*\\.");

    public void apply(ExtractElecDoc extract) {
        if (extract == null || !StringUtils.hasText(extract.getFileName()) || !N_FILE.matcher(extract.getFileName()).matches()) return;
        if (!StringUtils.hasText(extract.getContents())) return;
        String text = extract.getContents().replace("\r\n", "\n").replace('\r', '\n');
        extract.setHwpSubTitle(matchTitle(text));
        applyDocNoDate(text, IMPL, true, extract);
        applyDocNoDate(text, RECV, false, extract);
        String body = matchBody(text);
        if (StringUtils.hasText(body)) extract.setIndexingContents(body);
    }

    private String matchTitle(String text) {
        Matcher m = TITLE.matcher(text);
        return m.find() ? clean(m.group(1)) : null;
    }

    private void applyDocNoDate(String text, Pattern pattern, boolean implementation, ExtractElecDoc extract) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) return;
        String no = clean(m.group(1));
        String date = String.format("%04d-%02d-%02d", Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        if (implementation) {
            extract.setImplementOrg(no);
            extract.setImplementDate(date);
        } else {
            extract.setReceiptOrg(no);
            extract.setReceiptDate(date);
        }
    }

    private String matchBody(String text) {
        Matcher s = BODY_START.matcher(text);
        if (!s.find()) return null;
        int start = s.start();
        Matcher e = BODY_END.matcher(text);
        int end = text.length();
        if (e.find(start)) end = e.end();
        return text.substring(start, end).replaceAll("\\n{3,}", "\\n\\n").trim();
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
