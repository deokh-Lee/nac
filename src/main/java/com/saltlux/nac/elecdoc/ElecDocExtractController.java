package com.saltlux.nac.elecdoc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/electronic-documents/extract")
public class ElecDocExtractController {

    private final ElecDocExtractService elecDocExtractService;

    public ElecDocExtractController(ElecDocExtractService elecDocExtractService) {
        this.elecDocExtractService = elecDocExtractService;
    }

    @PostMapping
    public ResponseEntity<ExtractBatchResult> extractBatch(
            @RequestParam(required = false) String transferYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false, defaultValue = "false") boolean retryFail
    ) {
        return ResponseEntity.ok(elecDocExtractService.extractBatch(transferYear, limit, offset, retryFail));
    }

    @PostMapping("/all")
    public ResponseEntity<ExtractAllBatchResult> extractAll(
            @RequestParam(required = false) String transferYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(elecDocExtractService.extractAll(transferYear, limit, maxLoop, retryFail));
    }
}
