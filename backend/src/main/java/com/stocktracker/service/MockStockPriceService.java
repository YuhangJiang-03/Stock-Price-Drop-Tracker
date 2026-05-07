package com.stocktracker.service;

import com.stocktracker.dto.HistoryInterval;
import com.stocktracker.dto.PricePoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock implementation that simulates a noisy random walk around a per-symbol
 * "anchor" price. It's stateful so prices change a little each tick instead of
 * being completely random — that way the scheduler can plausibly observe a
 * "drop" relative to {@code highestPriceSeen}.
 *
 * <p>For history queries, we synthesise a deterministic random walk seeded by
 * {@code symbol + interval} and anchor its tail to the current spot price so
 * the chart "lands" where the dashboard says it should.
 *
 * <p>This bean only activates when {@code app.stock.provider=mock}; the
 * default provider is {@link YahooFinanceStockPriceService}.
 */
@Service
@ConditionalOnProperty(name = "app.stock.provider", havingValue = "mock")
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

    @Override
    public List<PricePoint> getHistory(String symbol, HistoryInterval interval) {
        String key = symbol.toUpperCase();
        // Make sure we have a current price so the synthetic series has
        // something stable to anchor against.
        double spot = lastPrice.computeIfAbsent(key, this::seedPrice);

        // Deterministic seed per (symbol, interval) so a refresh shows the
        // same curve. Using nextLong on hash combinations to spread bits.
        long seed = ((long) key.hashCode() << 16) ^ interval.ordinal();
        Random rng = new Random(seed);

        int n = interval.points();
        Duration step = interval.step();
        Instant now = Instant.now();
        Instant start = now.minus(step.multipliedBy(n - 1L));

        // Per-tick volatility scales with bucket size: a 1-day bar should move
        // more than a 15-minute bar.
        double sigma = volatilityFor(interval);

        // Walk forward from a random starting point near the spot so the
        // overall series brackets it, then renormalise so the last point
        // equals the spot exactly.
        double price = spot * (0.7 + rng.nextDouble() * 0.6); // 70%..130% of spot
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            double drift = rng.nextGaussian() * sigma;
            price = Math.max(1.0, price * (1.0 + drift));
            raw[i] = price;
        }
        double scale = spot / raw[n - 1];

        List<PricePoint> series = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal p = BigDecimal.valueOf(raw[i] * scale)
                .setScale(2, RoundingMode.HALF_UP);
            series.add(new PricePoint(start.plus(step.multipliedBy(i)), p));
        }
        return series;
    }

    /** Std-dev of per-tick % change. Bigger buckets = bigger moves. */
    private double volatilityFor(HistoryInterval interval) {
        return switch (interval) {
            case DAY -> 0.004;    // ~0.4% per 15 min
            case WEEK -> 0.010;   // ~1.0% per 2 hours
            case MONTH -> 0.022;  // ~2.2% per day
            case YEAR -> 0.035;   // ~3.5% per week
        };
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
