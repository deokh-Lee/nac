package com.saltlux.nac.prompt;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplateRenderer {

    public String render(String template, Map<String, ?> variables) {
        String rendered = template == null ? "" : template;
        if (variables == null || variables.isEmpty()) {
            return rendered;
        }

        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            rendered = rendered.replace("${" + entry.getKey() + "}", value);
            rendered = rendered.replace("{{" + entry.getKey() + "}}", escapeJsonStringValue(value));
            rendered = rendered.replace("{" + entry.getKey() + "}", value);
        }

        return rendered;
    }

    private String escapeJsonStringValue(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
