package com.stocktracker.service;

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
}
