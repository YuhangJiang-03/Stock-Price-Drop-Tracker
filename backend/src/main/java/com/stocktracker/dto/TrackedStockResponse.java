package com.stocktracker.dto;

import com.stocktracker.model.TrackedStock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Response payload for tracked-stock endpoints. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackedStockResponse {
    private Long id;
    private String symbol;
    private BigDecimal dropThresholdPercentage;
    private BigDecimal highestPriceSeen;
    private Instant lastNotifiedAt;
    private Instant createdAt;

    /** Convenience mapper to keep controllers slim. */
    public static TrackedStockResponse from(TrackedStock stock) {
        return TrackedStockResponse.builder()
            .id(stock.getId())
            .symbol(stock.getSymbol())
            .dropThresholdPercentage(stock.getDropThresholdPercentage())
            .highestPriceSeen(stock.getHighestPriceSeen())
            .lastNotifiedAt(stock.getLastNotifiedAt())
            .createdAt(stock.getCreatedAt())
            .build();
    }
}
