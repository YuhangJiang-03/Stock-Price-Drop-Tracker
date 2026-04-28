package com.stocktracker.service;

import com.stocktracker.dto.TrackedStockRequest;
import com.stocktracker.dto.TrackedStockResponse;
import com.stocktracker.exception.BadRequestException;
import com.stocktracker.exception.NotFoundException;
import com.stocktracker.model.TrackedStock;
import com.stocktracker.model.User;
import com.stocktracker.repository.TrackedStockRepository;
import com.stocktracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic around adding, listing and removing tracked stocks. All
 * methods are scoped to a specific user — callers pass in the email from the
 * authenticated principal.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final TrackedStockRepository trackedStockRepository;
    private final UserRepository userRepository;

    @Transactional
    public TrackedStockResponse addTrackedStock(String email, TrackedStockRequest request) {
        User user = loadUser(email);
        String symbol = request.getSymbol().trim().toUpperCase();

        if (trackedStockRepository.existsByUserAndSymbolIgnoreCase(user, symbol)) {
            throw new BadRequestException("You are already tracking " + symbol);
        }

        TrackedStock stock = TrackedStock.builder()
            .user(user)
            .symbol(symbol)
            .dropThresholdPercentage(request.getDropThresholdPercentage())
            .build();

        return TrackedStockResponse.from(trackedStockRepository.save(stock));
    }

    @Transactional(readOnly = true)
    public List<TrackedStockResponse> listTrackedStocks(String email) {
        User user = loadUser(email);
        return trackedStockRepository.findAllByUser(user).stream()
            .map(TrackedStockResponse::from)
            .toList();
    }

    @Transactional
    public void deleteTrackedStock(String email, Long stockId) {
        User user = loadUser(email);
        TrackedStock stock = trackedStockRepository.findByIdAndUser(stockId, user)
            .orElseThrow(() -> new NotFoundException("Tracked stock not found"));
        trackedStockRepository.delete(stock);
    }

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
