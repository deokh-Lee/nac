package com.saltlux.nac.elecdoc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
public class HtmlTextExtractionService {

    public TextExtractionResult extract(Path path) throws Exception {
        String html = Files.readString(path, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(html);
        Element body = document.body();
        String contents = body == null ? "" : extractElement(body).trim();
        boolean hasContents = contents != null && !contents.isBlank();
        return TextExtractionResult.withoutImages(contents, "text/html", hasContents);
    }

    private String extractElement(Element element) {
        if (element == null) {
            return "";
        }

        if ("table".equalsIgnoreCase(element.tagName())) {
            return extractTable(element);
        }

        StringBuilder sb = new StringBuilder();
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                appendText(sb, textNode.text());
            } else if (child instanceof Element childElement) {
                String tagName = childElement.tagName().toLowerCase();
                if (isHiddenOrIgnored(tagName)) {
                    continue;
                }
                if ("table".equals(tagName)) {
                    appendBlock(sb, extractTable(childElement));
                } else if ("br".equals(tagName)) {
                    sb.append('\n');
                } else if (isBlockTag(tagName)) {
                    appendBlock(sb, extractElement(childElement));
                } else {
                    appendText(sb, extractElement(childElement));
                }
            }
        }
        return normalize(sb.toString());
    }

    private String extractTable(Element table) {
        StringBuilder sb = new StringBuilder();
        Elements rows = table.select("tr");
        for (Element row : rows) {
            Elements cells = row.select("> th, > td");
            if (cells.isEmpty()) {
                cells = row.select("th, td");
            }

            StringBuilder rowText = new StringBuilder();
            for (Element cell : cells) {
                String cellText = extractElementWithoutNestedRowDuplication(cell);
                if (cellText.isBlank()) {
                    continue;
                }
                if (!rowText.isEmpty()) {
                    rowText.append(" | ");
                }
                rowText.append(cellText.replace('\n', ' ').replaceAll("\\s+", " ").trim());
            }

            if (!rowText.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(rowText);
            }
        }
        return sb.toString();
    }

    private String extractElementWithoutNestedRowDuplication(Element element) {
        Element clone = element.clone();
        clone.select("table").forEach(nested -> nested.replaceWith(new TextNode(extractTable(nested))));
        return extractElement(clone).replaceAll("\\s+", " ").trim();
    }

    private void appendText(StringBuilder sb, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n' && sb.charAt(sb.length() - 1) != ' ') {
            sb.append(' ');
        }
        sb.append(text.trim());
    }

    private void appendBlock(StringBuilder sb, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append(text.trim()).append('\n');
    }

    private boolean isHiddenOrIgnored(String tagName) {
        return "script".equals(tagName) || "style".equals(tagName) || "noscript".equals(tagName) || "head".equals(tagName);
    }

    private boolean isBlockTag(String tagName) {
        return "div".equals(tagName)
                || "p".equals(tagName)
                || "section".equals(tagName)
                || "article".equals(tagName)
                || "header".equals(tagName)
                || "footer".equals(tagName)
                || "h1".equals(tagName)
                || "h2".equals(tagName)
                || "h3".equals(tagName)
                || "h4".equals(tagName)
                || "h5".equals(tagName)
                || "h6".equals(tagName)
                || "ul".equals(tagName)
                || "ol".equals(tagName)
                || "li".equals(tagName)
                || "tr".equals(tagName);
    }

    private String normalize(String value) {
        return value
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
