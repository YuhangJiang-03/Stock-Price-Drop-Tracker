package com.stocktracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stocktracker.dto.NewsArticleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Trending-news implementation backed by Yahoo Finance's public search
 * endpoint ({@code query1.finance.yahoo.com/v1/finance/search}). The search
 * call is a side-channel for the autocomplete box — when given a generic
 * query like {@code "stock market"}, it returns a {@code news} array of the
 * most recent trending stock stories complete with thumbnails.
 *
 * <p>Like the price service, Yahoo's endpoint is unofficial. We send a
 * browser-style User-Agent and cache the result for a short TTL so a busy
 * dashboard doesn't repeatedly hammer the upstream.
 */
@Service
@Slf4j
public class YahooFinanceNewsService implements StockNewsService {

    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** Generic query that consistently returns trending market stories. */
    private static final String SEARCH_QUERY = "stock market";

    /** Pull a few extras so we can drop ones with no thumbnail and still hit {@code limit}. */
    private static final int FETCH_BUFFER = 10;

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestClient http;
    private final AtomicReference<CachedNews> cache = new AtomicReference<>();

    public YahooFinanceNewsService(
        @Value("${app.stock.yahoo.base-url:https://query1.finance.yahoo.com}") String baseUrl
    ) {
        this.http = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, UA)
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build();
    }

    @Override
    public List<NewsArticleResponse> getTrendingNews(int limit) {
        if (limit <= 0) return List.of();

        CachedNews cached = cache.get();
        if (cached != null && !cached.expired()) {
            return takeUpTo(cached.articles, limit);
        }

        List<NewsArticleResponse> fresh;
        try {
            fresh = fetchFromYahoo(Math.max(limit, FETCH_BUFFER));
        } catch (RestClientException ex) {
            log.warn("Yahoo Finance news request failed: {}", ex.getMessage());
            // Surface the previous (now stale) cache rather than propagating
            // a transient upstream blip — a slightly old headline beats an
            // error banner on the dashboard.
            if (cached != null) {
                return takeUpTo(cached.articles, limit);
            }
            throw new IllegalStateException("Could not reach Yahoo Finance: " + ex.getMessage(), ex);
        }

        cache.set(new CachedNews(fresh, Instant.now().plus(CACHE_TTL)));
        return takeUpTo(fresh, limit);
    }

    // ---- Yahoo HTTP --------------------------------------------------------

    private List<NewsArticleResponse> fetchFromYahoo(int newsCount) {
        JsonNode root = http.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/finance/search")
                .queryParam("q", SEARCH_QUERY)
                .queryParam("quotesCount", 0)
                .queryParam("newsCount", newsCount)
                .build())
            .retrieve()
            .body(JsonNode.class);

        if (root == null) {
            return List.of();
        }
        JsonNode news = root.path("news");
        if (!news.isArray()) {
            return List.of();
        }

        List<NewsArticleResponse> out = new ArrayList<>(news.size());
        for (JsonNode item : news) {
            NewsArticleResponse article = parseArticle(item);
            if (article != null) {
                out.add(article);
            }
        }
        return out;
    }

    /**
     * Map a single Yahoo {@code news[]} entry to our DTO. Returns {@code null}
     * when the entry is missing the bare essentials (title + link) — those
     * aren't useful to render on the dashboard.
     */
    private NewsArticleResponse parseArticle(JsonNode item) {
        String title = textOrNull(item, "title");
        String link = textOrNull(item, "link");
        if (title == null || link == null) {
            return null;
        }

        String uuid = textOrNull(item, "uuid");
        String publisher = textOrNull(item, "publisher");
        Instant publishedAt = item.hasNonNull("providerPublishTime")
            ? Instant.ofEpochSecond(item.get("providerPublishTime").asLong())
            : null;

        String imageUrl = pickThumbnail(item.path("thumbnail"));

        List<String> tickers = new ArrayList<>();
        JsonNode rt = item.path("relatedTickers");
        if (rt.isArray()) {
            for (JsonNode t : rt) {
                String sym = t.asText(null);
                if (sym != null && !sym.isBlank()) tickers.add(sym);
            }
        }

        return NewsArticleResponse.builder()
            .id(uuid != null ? uuid : link)
            .title(title)
            .publisher(publisher)
            .articleUrl(link)
            .imageUrl(imageUrl)
            .publishedAt(publishedAt)
            .relatedTickers(tickers)
            .build();
    }

    /**
     * Yahoo's {@code thumbnail.resolutions} contains the same image at multiple
     * sizes. We prefer the biggest one tagged {@code "original"} for the hero
     * card; if that's missing, fall back to whichever is largest.
     */
    private String pickThumbnail(JsonNode thumbnail) {
        JsonNode resolutions = thumbnail.path("resolutions");
        if (!resolutions.isArray() || resolutions.isEmpty()) {
            return null;
        }
        String fallback = null;
        long fallbackArea = -1;
        for (JsonNode r : resolutions) {
            String url = textOrNull(r, "url");
            if (url == null) continue;

            if ("original".equalsIgnoreCase(r.path("tag").asText(""))) {
                return url;
            }
            long area = (long) r.path("width").asInt(0) * r.path("height").asInt(0);
            if (area > fallbackArea) {
                fallbackArea = area;
                fallback = url;
            }
        }
        return fallback;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText("");
        return s.isBlank() ? null : s;
    }

    private static List<NewsArticleResponse> takeUpTo(List<NewsArticleResponse> source, int limit) {
        if (source.size() <= limit) return List.copyOf(source);
        return List.copyOf(source.subList(0, limit));
    }

    private record CachedNews(List<NewsArticleResponse> articles, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
