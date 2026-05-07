package com.stocktracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** A single (timestamp, price) sample on a price-history curve. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PricePoint {
    private Instant timestamp;
    private BigDecimal price;
}
