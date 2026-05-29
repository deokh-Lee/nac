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
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
            rendered = rendered.replace("{" + entry.getKey() + "}", value);
        }

        return rendered;
    }
}
