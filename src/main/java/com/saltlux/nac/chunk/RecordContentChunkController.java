package com.saltlux.nac.chunk;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/electronic-documents/chunks")
public class RecordContentChunkController {

    private final RecordContentChunkService chunkService;

    public RecordContentChunkController(RecordContentChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @PostMapping
    public ResponseEntity<ChunkBatchResult> chunkBatch(
            @RequestParam(required = false) String transferYear,
            @RequestParam(required = false) Integer dataYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer chunkSize,
            @RequestParam(required = false) Integer overlapSize,
            @RequestParam(required = false, defaultValue = "false") boolean recreate
    ) {
        return ResponseEntity.ok(chunkService.chunkBatch(transferYear, dataYear, limit, offset, chunkSize, overlapSize, recreate));
    }

    @PostMapping("/all")
    public ResponseEntity<ChunkAllBatchResult> chunkAll(
            @RequestParam(required = false) String transferYear,
            @RequestParam(required = false) Integer dataYear,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer maxLoop,
            @RequestParam(required = false) Integer chunkSize,
            @RequestParam(required = false) Integer overlapSize,
            @RequestParam(required = false, defaultValue = "false") boolean recreate
    ) {
        return ResponseEntity.ok(chunkService.chunkAll(transferYear, dataYear, limit, maxLoop, chunkSize, overlapSize, recreate));
    }
}
