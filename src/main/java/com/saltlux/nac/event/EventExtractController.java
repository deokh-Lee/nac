package com.saltlux.nac.event;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/electronic-documents/event-extract")
public class EventExtractController {

    private final EventExtractService eventExtractService;

    public EventExtractController(EventExtractService eventExtractService) {
        this.eventExtractService = eventExtractService;
    }

    @PostMapping
    public ResponseEntity<EventExtractResult> extractBatch(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(eventExtractService.extractBatch(transferYear, prodYear, limit, offset, retryFail));
    }

    @PostMapping("/all")
    public ResponseEntity<EventExtractAllResult> extractAll(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(eventExtractService.extractAll(transferYear, prodYear, limit, maxLoop, retryFail));
    }
}
