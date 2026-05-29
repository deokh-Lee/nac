package com.saltlux.nac.policy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/electronic-documents/policy-extract")
public class PolicyExtractController {

    private final PolicyExtractService policyExtractService;

    public PolicyExtractController(PolicyExtractService policyExtractService) {
        this.policyExtractService = policyExtractService;
    }

    @PostMapping
    public ResponseEntity<PolicyExtractResult> extractBatch(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(policyExtractService.extractBatch(transferYear, prodYear, limit, offset, retryFail));
    }

    @PostMapping("/all")
    public ResponseEntity<PolicyExtractAllResult> extractAll(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(policyExtractService.extractAll(transferYear, prodYear, limit, maxLoop, retryFail));
    }
}
