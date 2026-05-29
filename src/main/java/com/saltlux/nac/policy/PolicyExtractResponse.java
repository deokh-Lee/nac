package com.saltlux.nac.policy;

public record PolicyExtractResponse(
        String rcCode,
        String rcRfileNo,
        String rcRitemNo,
        String bndTtl,
        String jemok,
        String policy,
        String itemCd,
        String reason
) {
}
