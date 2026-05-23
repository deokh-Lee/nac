package com.saltlux.nac.record;

import java.time.LocalDateTime;

public record RecordResponse(
        Long id,
        String title,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RecordResponse from(Record record) {
        return new RecordResponse(
                record.getId(),
                record.getTitle(),
                record.getDescription(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
