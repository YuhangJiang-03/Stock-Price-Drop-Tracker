package com.stocktracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stocktracker.dto.HistoryInterval;
import com.stocktracker.dto.PricePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-data implementation backed by Yahoo Finance's public v8 chart endpoint
 * ({@code query1.finance.yahoo.com/v8/finance/chart/{symbol}}). It returns
 * both the current price (via {@code meta.regularMarketPrice}) and an OHLCV
 * time series for the requested window in a single response.
 *
 * <p>Yahoo's endpoint is unofficial — there's no SLA, no published rate limit,
 * and a browser-style User-Agent header is required. To stay polite we cache
 * responses for a short TTL keyed by (symbol, interval) so the scheduler
 * polling every few minutes doesn't generate one HTTP call per stock per tick.
 *
 * <p>This bean only activates when {@code app.stock.provider=yahoo} (the
 * default). Set the property to {@code mock} to fall back to the deterministic
 * random-walk service for offline/dev work.
 */
@Service
@ConditionalOnProperty(name = "app.stock.provider", havingValue = "yahoo", matchIfMissing = true)
@Slf4j
public class YahooFinanceStockPriceService implements StockPriceService {

    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final RestClient http;
    private final Map<String, CachedPrice> priceCache = new ConcurrentHashMap<>();
    private final Map<HistoryKey, CachedHistory> historyCache = new ConcurrentHashMap<>();

    public YahooFinanceStockPriceService(
        @Value("${app.stock.yahoo.base-url:https://query1.finance.yahoo.com}") String baseUrl
    ) {
        this.http = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, UA)
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build();
    }

    // ---- StockPriceService -------------------------------------------------

    @Override
    public double getCurrentPrice(String symbol) {
        String key = symbol.toUpperCase();
        CachedPrice cached = priceCache.get(key);
        if (cached != null && !cached.expired()) {
            return cached.price;
        }

        // The cheapest call that gives us regularMarketPrice. We deliberately
        // don't piggy-back on a history call here because the scheduler often
        // wants the spot price more frequently than it wants a fresh chart.
        JsonNode root = fetchChart(key, "1d", "1d");
        JsonNode meta = requireResult(root, key).path("meta");
        double price = meta.path("regularMarketPrice").asDouble(Double.NaN);
        if (Double.isNaN(price)) {
            // Fallback: take the last close from the indicators array.
            price = lastClose(root)
                .orElseThrow(() -> new IllegalStateException(
                    "Yahoo Finance returned no usable price for " + key));
        }
        priceCache.put(key, new CachedPrice(price, Instant.now().plus(PRICE_TTL)));
        return price;
    }

    @Override
    public List<PricePoint> getHistory(String symbol, HistoryInterval interval) {
        String key = symbol.toUpperCase();
        HistoryKey cacheKey = new HistoryKey(key, interval);
        CachedHistory cached = historyCache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            return cached.points;
        }

        YahooParams params = paramsFor(interval);
        JsonNode root = fetchChart(key, params.range, params.granularity);
        JsonNode result = requireResult(root, key);

        List<PricePoint> points = parsePoints(result);
        if (points.isEmpty()) {
            throw new IllegalStateException(
                "Yahoo Finance returned no history points for " + key + " (" + interval + ")");
        }

        // Side-benefit: keep the spot-price cache fresh from the meta block
        // since Yahoo gives it to us for free on every chart response.
        double regular = result.path("meta").path("regularMarketPrice").asDouble(Double.NaN);
        if (!Double.isNaN(regular)) {
            priceCache.put(key, new CachedPrice(regular, Instant.now().plus(PRICE_TTL)));
        }

        historyCache.put(cacheKey, new CachedHistory(points, Instant.now().plus(historyTtl(interval))));
        return points;
    }

    // ---- Yahoo HTTP --------------------------------------------------------

    private JsonNode fetchChart(String symbol, String range, String granularity) {
        try {
            return http.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/v8/finance/chart/{symbol}")
                    .queryParam("range", range)
                    .queryParam("interval", granularity)
                    .queryParam("includePrePost", false)
                    .queryParam("events", "div,splits")
                    .build(symbol))
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException ex) {
            log.warn("Yahoo Finance request failed for {} ({}/{}): {}",
                symbol, range, granularity, ex.getMessage());
            throw new IllegalStateException(
                "Could not reach Yahoo Finance: " + ex.getMessage(), ex);
        }
    }

    /**
     * Pull {@code chart.result[0]} out of the Yahoo response and surface a
     * useful error if it's missing (typically because the symbol is unknown).
     */
    private JsonNode requireResult(JsonNode root, String symbol) {
        if (root == null) {
            throw new IllegalStateException("Empty Yahoo response for " + symbol);
        }
        JsonNode error = root.path("chart").path("error");
        if (error != null && !error.isNull() && !error.isMissingNode()) {
            String desc = error.path("description").asText("Unknown error");
            throw new IllegalArgumentException("Yahoo Finance error for " + symbol + ": " + desc);
        }
        JsonNode results = root.path("chart").path("result");
        if (!results.isArray() || results.isEmpty()) {
            throw new IllegalArgumentException("Yahoo Finance returned no result for " + symbol);
        }
        return results.get(0);
    }

    private List<PricePoint> parsePoints(JsonNode result) {
        JsonNode timestamps = result.path("timestamp");
        JsonNode quoteArr = result.path("indicators").path("quote");
        if (!timestamps.isArray() || !quoteArr.isArray() || quoteArr.isEmpty()) {
            return List.of();
        }
        JsonNode closes = quoteArr.get(0).path("close");
        int n = timestamps.size();

        List<PricePoint> out = new ArrayList<>(n);
        Double lastValid = null;
        for (int i = 0; i < n; i++) {
            JsonNode closeNode = closes.get(i);
            // Yahoo returns nulls for non-trading minutes inside the bucket
            // array; carry the last known close forward so the line stays
            // continuous instead of disappearing into gaps.
            if (closeNode != null && !closeNode.isNull()) {
                lastValid = closeNode.asDouble();
            }
            if (lastValid == null) continue;

            Instant ts = Instant.ofEpochSecond(timestamps.get(i).asLong());
            BigDecimal price = BigDecimal.valueOf(lastValid).setScale(2, RoundingMode.HALF_UP);
            out.add(new PricePoint(ts, price));
        }
        return out;
    }

    private java.util.OptionalDouble lastClose(JsonNode root) {
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) return java.util.OptionalDouble.empty();
        JsonNode closes = result.get(0).path("indicators").path("quote").path(0).path("close");
        for (int i = closes.size() - 1; i >= 0; i--) {
            JsonNode v = closes.get(i);
            if (v != null && !v.isNull()) return java.util.OptionalDouble.of(v.asDouble());
        }
        return java.util.OptionalDouble.empty();
    }

    /**
     * Map our {@link HistoryInterval} to Yahoo's {@code range}/{@code interval}
     * pair. Yahoo only returns trading-session data, so the resulting series
     * looks more like a real broker chart than a uniform 24/7 feed.
     */
    private YahooParams paramsFor(HistoryInterval interval) {
        return switch (interval) {
            case DAY -> new YahooParams("1d", "15m");
            case WEEK -> new YahooParams("5d", "30m");
            case MONTH -> new YahooParams("1mo", "1d");
            case YEAR -> new YahooParams("1y", "1wk");
        };
    }

    // ---- Cache primitives --------------------------------------------------

    private static final Duration PRICE_TTL = Duration.ofSeconds(60);

    private static Duration historyTtl(HistoryInterval interval) {
        return switch (interval) {
            case DAY -> Duration.ofMinutes(5);
            case WEEK -> Duration.ofMinutes(30);
            case MONTH -> Duration.ofHours(1);
            case YEAR -> Duration.ofHours(6);
        };
    }

    private record YahooParams(String range, String granularity) {}

    private record CachedPrice(double price, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private record CachedHistory(List<PricePoint> points, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private record HistoryKey(String symbol, HistoryInterval interval) {}
}
