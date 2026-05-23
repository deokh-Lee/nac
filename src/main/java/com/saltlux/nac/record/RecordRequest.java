package com.saltlux.nac.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordRequest(
        @NotBlank(message = "title is required")
        @Size(max = 300, message = "title must be less than 300 characters")
        String title,

        String description
) {
}
