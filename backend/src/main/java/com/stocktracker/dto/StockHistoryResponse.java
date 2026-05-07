package com.stocktracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response payload for {@code GET /stocks/{id}/history}. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockHistoryResponse {
    private String symbol;
    private HistoryInterval interval;
    private List<PricePoint> points;
}
