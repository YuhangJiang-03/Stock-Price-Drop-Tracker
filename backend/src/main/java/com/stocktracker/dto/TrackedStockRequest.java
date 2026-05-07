package com.stocktracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/** Payload for {@code POST /stocks}. */
@Data
public class TrackedStockRequest {

    /** Ticker symbol; we accept 1-10 alphanumeric chars (e.g. {@code BRK.B}). */
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9.\\-]{1,10}$", message = "Invalid ticker symbol")
    private String symbol;

    @NotNull
    @DecimalMin(value = "0.01", message = "Drop threshold must be greater than 0")
    @DecimalMax(value = "100.00", message = "Drop threshold cannot exceed 100%")
    private BigDecimal dropThresholdPercentage;

    /**
     * Optional rise threshold. When omitted, no rise alerts fire for this
     * stock. Same numeric range as the drop threshold.
     */
    @DecimalMin(value = "0.01", message = "Rise threshold must be greater than 0")
    @DecimalMax(value = "1000.00", message = "Rise threshold cannot exceed 1000%")
    private BigDecimal riseThresholdPercentage;
}
