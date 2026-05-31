package com.saltlux.nac.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    @Test
    void renderEscapesDoubleBracesForJsonStringValues() {
        String template = """
                {
                  "JEMOK": "{{JEMOK}}"
                }
                """;

        String rendered = renderer.render(template, Map.of(
                "JEMOK", "\"quoted title\" review"
        ));

        assertThat(rendered).contains(
                "\"JEMOK\": \"\\\"quoted title\\\" review\""
        );
    }

    @Test
    void renderKeepsDollarBracesAsRawJson() {
        String template = """
                [candidate list]
                ${candidate_event_list_json}
                """;

        String rendered = renderer.render(template, Map.of(
                "candidate_event_list_json", "[{\"ITEM_CD\":\"LMB_EVENT_00000015\"}]"
        ));

        assertThat(rendered).contains("[{\"ITEM_CD\":\"LMB_EVENT_00000015\"}]");
    }
}
