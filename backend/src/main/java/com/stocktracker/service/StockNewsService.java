package com.stocktracker.service;

import com.stocktracker.dto.NewsArticleResponse;

import java.util.List;

/**
 * Abstraction over an external trending-stock-news provider.
 *
 * <p>A concrete implementation should be registered as a Spring bean.
 * {@link YahooFinanceNewsService} backs the default deployment with Yahoo
 * Finance's public search endpoint (no API key required).
 */
public interface StockNewsService {

    /**
     * Return the top {@code limit} trending stock-market news articles,
     * newest first.
     *
     * @param limit maximum number of articles to return; the implementation
     *              may return fewer if the provider has fewer relevant items
     * @return ordered list of {@link NewsArticleResponse} (never {@code null})
     */
    List<NewsArticleResponse> getTrendingNews(int limit);
}
