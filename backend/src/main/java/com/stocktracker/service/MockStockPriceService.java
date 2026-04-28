package com.stocktracker.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock implementation that simulates a noisy random walk around a per-symbol
 * "anchor" price. It's stateful so prices change a little each tick instead of
 * being completely random — that way the scheduler can plausibly observe a
 * "drop" relative to {@code highestPriceSeen}.
 */
@Service
public class MockStockPriceService implements StockPriceService {

    /** Per-symbol last observed price. */
    private final Map<String, Double> lastPrice = new ConcurrentHashMap<>();

    @Override
    public double getCurrentPrice(String symbol) {
        String key = symbol.toUpperCase();
        return lastPrice.compute(key, (sym, prev) -> {
            double base = prev != null ? prev : seedPrice(sym);
            // Random walk: -8% .. +8% per tick.
            double pctChange = ThreadLocalRandom.current().nextDouble(-0.08, 0.08);
            double next = base * (1 + pctChange);
            // Keep the price within sane bounds.
            return Math.max(1.0, Math.round(next * 100.0) / 100.0);
        });
    }

    /**
     * Pick a stable starting price for a symbol so the same ticker doesn't
     * begin its life at $1 every restart.
     */
    private double seedPrice(String symbol) {
        int hash = Math.abs(symbol.hashCode());
        return 50.0 + (hash % 450); // $50 .. $499
    }
}
