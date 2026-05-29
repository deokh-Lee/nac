package com.saltlux.nac.activity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/electronic-documents/activity-extract")
public class ActivityExtractController {

    private final ActivityExtractService activityExtractService;

    public ActivityExtractController(ActivityExtractService activityExtractService) {
        this.activityExtractService = activityExtractService;
    }

    @PostMapping
    public ResponseEntity<ActivityExtractResult> extractBatch(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(activityExtractService.extractBatch(transferYear, prodYear, limit, offset, retryFail));
    }

    @PostMapping("/all")
    public ResponseEntity<ActivityExtractAllResult> extractAll(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(activityExtractService.extractAll(transferYear, prodYear, limit, maxLoop, retryFail));
    }
}
