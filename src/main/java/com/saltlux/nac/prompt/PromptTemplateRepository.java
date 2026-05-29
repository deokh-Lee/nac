package com.saltlux.nac.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

@Component
public class PromptTemplateRepository {

    private static final String PROMPT_RESOURCE_PATTERN = "classpath*:com/saltlux/nac/prompt/*.txt";
    private static final String TXT_SUFFIX = ".txt";

    private final Map<String, PromptTemplate> templates;

    public PromptTemplateRepository() {
        this.templates = loadTemplates();
    }

    public PromptTemplate get(String promptName) {
        String normalizedName = normalizePromptName(promptName);
        PromptTemplate template = templates.get(normalizedName);
        if (template == null) {
            throw new IllegalArgumentException("Unknown prompt: " + promptName + ". available=" + templates.keySet());
        }
        return template;
    }

    public PromptTemplate getOrDefault(String promptName, String defaultPromptName) {
        String selectedPromptName = StringUtils.hasText(promptName) ? promptName : defaultPromptName;
        return get(selectedPromptName);
    }

    public List<String> names() {
        return List.copyOf(templates.keySet());
    }

    private Map<String, PromptTemplate> loadTemplates() {
        Resource[] resources = findPromptResources();
        Map<String, PromptTemplate> loadedTemplates = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (!StringUtils.hasText(filename) || !filename.endsWith(TXT_SUFFIX)) {
                continue;
            }

            String name = normalizePromptName(filename);
            if (!StringUtils.hasText(name)) {
                continue;
            }

            loadedTemplates.put(name, new PromptTemplate(name, read(resource), describe(resource)));
        }

        if (loadedTemplates.isEmpty()) {
            throw new IllegalStateException("No prompt files found: " + PROMPT_RESOURCE_PATTERN);
        }

        return Collections.unmodifiableMap(loadedTemplates);
    }

    private Resource[] findPromptResources() {
        try {
            return new PathMatchingResourcePatternResolver().getResources(PROMPT_RESOURCE_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to find prompt files: " + PROMPT_RESOURCE_PATTERN, e);
        }
    }

    private String read(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt file: " + describe(resource), e);
        }
    }

    private String describe(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }

    private String normalizePromptName(String promptName) {
        if (!StringUtils.hasText(promptName)) {
            return "";
        }

        String normalized = promptName.trim();
        if (normalized.endsWith(TXT_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - TXT_SUFFIX.length());
        }
        return normalized;
    }
}
