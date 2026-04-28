package com.stocktracker.controller;

import com.stocktracker.dto.TrackedStockRequest;
import com.stocktracker.dto.TrackedStockResponse;
import com.stocktracker.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authenticated endpoints for managing the caller's tracked stocks. The
 * authenticated user's email is resolved from the JWT-populated principal.
 */
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping
    public ResponseEntity<TrackedStockResponse> add(
        @AuthenticationPrincipal UserDetails principal,
        @Valid @RequestBody TrackedStockRequest request
    ) {
        return ResponseEntity.ok(stockService.addTrackedStock(principal.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<List<TrackedStockResponse>> list(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(stockService.listTrackedStocks(principal.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal UserDetails principal,
        @PathVariable Long id
    ) {
        stockService.deleteTrackedStock(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
