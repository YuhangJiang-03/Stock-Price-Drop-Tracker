package com.stocktracker.scheduler;

import com.stocktracker.model.TrackedStock;
import com.stocktracker.repository.TrackedStockRepository;
import com.stocktracker.service.NotificationService;
import com.stocktracker.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodically checks every tracked stock and triggers a notification when its
 * price has dropped from {@code highestPriceSeen} by more than the user's
 * threshold. Runs every 5 minutes by default ({@code app.scheduler.price-check-cron}).
 *
 * <p>Each user is rate-limited via {@link #cooldown} so they aren't spammed if
 * a stock keeps oscillating below the threshold.
 *
 * <p>The actual delivery channel (log line, email, etc.) is decided by the
 * {@link NotificationService} bean wired in — see
 * {@code app.notification.channel} in {@code application.yml}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockPriceScheduler {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TrackedStockRepository trackedStockRepository;
    private final StockPriceService stockPriceService;
    private final NotificationService notificationService;

    @Value("${app.scheduler.notification-cooldown-minutes:60}")
    private long cooldownMinutes;

    private Duration cooldown() {
        return Duration.ofMinutes(cooldownMinutes);
    }

    /**
     * Cron-based schedule (defaults to every 5 minutes). Wrapped in a single
     * transaction so dirty entities are flushed at the end. For very large
     * fleets this should be paged + parallelised.
     */
    @Scheduled(cron = "${app.scheduler.price-check-cron:0 */5 * * * *}")
    @Transactional
    public void checkPrices() {
        List<TrackedStock> stocks = trackedStockRepository.findAll();
        if (stocks.isEmpty()) {
            return;
        }

        log.info("Running price check across {} tracked stocks", stocks.size());

        for (TrackedStock stock : stocks) {
            try {
                processStock(stock);
            } catch (Exception ex) {
                // Don't let one bad symbol break the whole run.
                log.warn("Price check failed for {} (id={}): {}",
                    stock.getSymbol(), stock.getId(), ex.getMessage());
            }
        }
    }

    private void processStock(TrackedStock stock) {
        double rawPrice = stockPriceService.getCurrentPrice(stock.getSymbol());
        BigDecimal currentPrice = BigDecimal.valueOf(rawPrice).setScale(4, RoundingMode.HALF_UP);

        BigDecimal high = stock.getHighestPriceSeen();
        if (high == null || currentPrice.compareTo(high) > 0) {
            stock.setHighestPriceSeen(currentPrice);
            return; // New high water mark; no drop to evaluate.
        }

        BigDecimal dropPct = computeDropPercent(high, currentPrice);
        BigDecimal threshold = stock.getDropThresholdPercentage();

        if (dropPct.compareTo(threshold) < 0) {
            return;
        }

        if (isInCooldown(stock)) {
            log.debug("Skipping alert for {}: still in cooldown window", stock.getSymbol());
            return;
        }

        sendDropAlert(stock, currentPrice, high, dropPct);
        stock.setLastNotifiedAt(Instant.now());
    }

    /** drop% = (high - current) / high * 100 */
    private BigDecimal computeDropPercent(BigDecimal high, BigDecimal current) {
        return high.subtract(current)
            .divide(high, 6, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isInCooldown(TrackedStock stock) {
        Instant last = stock.getLastNotifiedAt();
        return last != null && Duration.between(last, Instant.now()).compareTo(cooldown()) < 0;
    }

    private void sendDropAlert(TrackedStock stock, BigDecimal current, BigDecimal high, BigDecimal dropPct) {
        String subject = String.format("Price alert: %s down %s%%",
            stock.getSymbol(), dropPct.toPlainString());
        String body = String.format(
            "%s is now $%s, down %s%% from a recent high of $%s.%n%n"
                + "You set a %s%% drop threshold for this stock.%n%n"
                + "— Stock Price Tracker",
            stock.getSymbol(),
            current.toPlainString(),
            dropPct.toPlainString(),
            high.toPlainString(),
            stock.getDropThresholdPercentage().toPlainString()
        );
        notificationService.notify(stock.getUser(), subject, body);
    }
}
