package com.saltlux.nac.activity;

public record ActivityExtractResponse(
        String rcCode,
        String rcRfileNo,
        String rcRitemNo,
        String bndTtl,
        String jemok,
        String activityName,
        String itemCd,
        String reason
) {
}
