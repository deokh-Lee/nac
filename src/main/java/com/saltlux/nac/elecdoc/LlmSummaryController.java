package com.saltlux.nac.elecdoc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/electronic-documents/llm-summary")
public class LlmSummaryController {

    private final LlmSummaryService llmSummaryService;

    public LlmSummaryController(LlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
    }

    @PostMapping
    public ResponseEntity<LlmSummaryBatchResult> summarizeBatch(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail,
            @RequestParam(required = false) String promptName
    ) {
        return ResponseEntity.ok(llmSummaryService.summarizeBatch(transferYear, limit, retryFail, promptName));
    }

    @PostMapping("/all")
    public ResponseEntity<LlmSummaryAllBatchResult> summarizeAll(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail,
            @RequestParam(required = false) String promptName
    ) {
        return ResponseEntity.ok(llmSummaryService.summarizeAll(transferYear, limit, maxLoop, retryFail, promptName));
    }
}
