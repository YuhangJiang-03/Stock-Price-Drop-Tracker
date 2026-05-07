package com.stocktracker.controller;

import com.stocktracker.dto.NewsArticleResponse;
import com.stocktracker.service.StockNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authenticated endpoints for trending stock-market news. Used by the
 * dashboard hero strip on the homepage.
 *
 * <p>Like the rest of the API, this requires a JWT — it sits behind the
 * default-deny rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    /** Hard ceiling so a curious caller can't ask the upstream for hundreds of articles. */
    private static final int MAX_LIMIT = 20;

    private final StockNewsService stockNewsService;

    @GetMapping("/trending")
    public ResponseEntity<List<NewsArticleResponse>> trending(
        @RequestParam(name = "limit", defaultValue = "3") int limit
    ) {
        int effective = Math.max(1, Math.min(MAX_LIMIT, limit));
        return ResponseEntity.ok(stockNewsService.getTrendingNews(effective));
    }
}
