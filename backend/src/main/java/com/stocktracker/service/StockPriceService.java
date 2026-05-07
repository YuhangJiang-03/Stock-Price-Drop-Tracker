package com.stocktracker.service;

import com.stocktracker.dto.HistoryInterval;
import com.stocktracker.dto.PricePoint;

import java.util.List;

/**
 * Abstraction over an external stock-price provider (Yahoo Finance,
 * Alpha Vantage, Finnhub, IEX Cloud, ...).
 *
 * <p>A concrete implementation should be registered as a Spring bean. The mock
 * implementation in {@code MockStockPriceService} is used for local
 * development and tests.
 */
public interface StockPriceService {

    /**
     * Fetch the latest price for the given ticker.
     *
     * @param symbol e.g. {@code "AAPL"}
     * @return the most recent price, in USD, as a primitive {@code double} per
     *         the original spec.
     * @throws IllegalArgumentException if the symbol is unknown
     */
    double getCurrentPrice(String symbol);

    /**
     * Return historical price samples for the given symbol over the requested
     * window. The list is ordered chronologically (oldest first) and the last
     * point should align reasonably closely with the current spot price.
     *
     * @param symbol   e.g. {@code "AAPL"}
     * @param interval the time window + bucket granularity to sample
     * @return ordered list of {@link PricePoint} samples
     */
    List<PricePoint> getHistory(String symbol, HistoryInterval interval);
}
