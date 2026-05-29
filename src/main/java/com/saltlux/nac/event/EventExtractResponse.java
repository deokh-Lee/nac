package com.saltlux.nac.event;

public record EventExtractResponse(
        String rcCode,
        String rcRfileNo,
        String rcRitemNo,
        String bndTtl,
        String jemok,
        String eventName,
        String itemCd,
        String reason
) {
}
