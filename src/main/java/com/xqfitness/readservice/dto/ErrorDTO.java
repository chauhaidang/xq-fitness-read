package com.xqfitness.readservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDTO {
    private String code;
    private String message;
    private OffsetDateTime timestamp;

    public ErrorDTO(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
