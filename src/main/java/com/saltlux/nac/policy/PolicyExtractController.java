package com.saltlux.nac.policy;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/departments/targets")
    public ResponseEntity<List<PolicyExtractTarget>> findDepartmentTargets(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return ResponseEntity.ok(policyExtractService.findDepartmentTargets(transferYear, prodYear, limit, offset));
    }

    @GetMapping({"/departments/items/targets", "/departments/item/targets"})
    public ResponseEntity<List<PolicyExtractTarget>> findDepartmentItemCompareTargets(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return ResponseEntity.ok(policyExtractService.findDepartmentItemCompareTargets(transferYear, prodYear, limit, offset));
    }

    @PostMapping("/departments")
    public ResponseEntity<PolicyExtractResult> extractDepartmentBatch(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return ResponseEntity.ok(policyExtractService.extractDepartmentBatch(transferYear, prodYear, limit, offset));
    }

    @PostMapping("/departments/all")
    public ResponseEntity<PolicyExtractAllResult> extractDepartmentAll(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop
    ) {
        return ResponseEntity.ok(policyExtractService.extractDepartmentAll(transferYear, prodYear, limit, maxLoop));
    }

    @PostMapping("/departments/file")
    public ResponseEntity<PolicyExtractResult> extractDepartmentFileBatch(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return ResponseEntity.ok(policyExtractService.extractDepartmentFileBatch(transferYear, prodYear, limit, offset));
    }

    @PostMapping("/departments/file/all")
    public ResponseEntity<PolicyExtractAllResult> extractDepartmentFileAll(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop
    ) {
        return ResponseEntity.ok(policyExtractService.extractDepartmentFileAll(transferYear, prodYear, limit, maxLoop));
    }

    @PostMapping({"/departments/items", "/departments/item"})
    public ResponseEntity<PolicyExtractResult> extractDepartmentItemCompareBatch(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return ResponseEntity.ok(policyExtractService.extractDepartmentItemCompareBatch(transferYear, prodYear, limit, offset));
    }

    @PostMapping({"/departments/items/all", "/departments/item/all"})
    public ResponseEntity<PolicyExtractAllResult> extractDepartmentItemCompareAll(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop
    ) {
        return ResponseEntity.ok(policyExtractService.extractDepartmentItemCompareAll(transferYear, prodYear, limit, maxLoop));
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

    @PostMapping("/sample")
    public ResponseEntity<PolicyExtractResult> extractSample(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false, defaultValue = "2012") String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(policyExtractService.extractSample(transferYear, prodYear, limit, offset, retryFail));
    }
}
