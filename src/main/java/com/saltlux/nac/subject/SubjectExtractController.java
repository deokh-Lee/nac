package com.saltlux.nac.subject;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/electronic-documents/subject-extract")
public class SubjectExtractController {

    private final SubjectExtractService subjectExtractService;

    public SubjectExtractController(SubjectExtractService subjectExtractService) {
        this.subjectExtractService = subjectExtractService;
    }

    @PostMapping
    public ResponseEntity<SubjectExtractResult> extractBatch(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(subjectExtractService.extractBatch(transferYear, prodYear, limit, offset, retryFail));
    }

    @PostMapping("/all")
    public ResponseEntity<SubjectExtractAllResult> extractAll(
            @RequestParam(required = false, defaultValue = "2023") String transferYear,
            @RequestParam(required = false) String prodYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop,
            @RequestParam(required = false, defaultValue = "false") Boolean retryFail
    ) {
        return ResponseEntity.ok(subjectExtractService.extractAll(transferYear, prodYear, limit, maxLoop, retryFail));
    }
}
