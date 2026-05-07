package com.stocktracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * A single trending stock-news article surfaced to the frontend.
 *
 * <p>The shape is intentionally provider-agnostic: the news service massages
 * Yahoo's response (or any other future provider) into this DTO so the UI
 * doesn't have to know where the article came from.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NewsArticleResponse {
    /** Stable id for React keys; falls back to the article URL when absent. */
    private String id;
    private String title;
    private String publisher;
    /** Direct link to the full article on the publisher's site. */
    private String articleUrl;
    /** Best-available thumbnail; may be {@code null} when the source has none. */
    private String imageUrl;
    private Instant publishedAt;
    /** Tickers Yahoo associates with the story (handy for chips on the card). */
    private List<String> relatedTickers;
}
