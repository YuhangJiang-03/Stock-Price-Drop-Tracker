package com.stocktracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned by {@code GlobalExceptionHandler}. */
@Data
@AllArgsConstructor
public class ApiError {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    /** Optional per-field validation errors. */
    private Map<String, String> fieldErrors;
}
