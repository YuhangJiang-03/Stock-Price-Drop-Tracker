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
 * Periodically checks every tracked stock and triggers a notification when:
 * <ul>
 *   <li>its price has dropped from {@code highestPriceSeen} by more than the
 *       user's drop threshold, or</li>
 *   <li>(if a rise threshold is configured) its price has risen from
 *       {@code lowestPriceSeen} by more than the rise threshold.</li>
 * </ul>
 *
 * <p>Runs every 5 minutes by default ({@code app.scheduler.price-check-cron}).
 * Each direction has its own cooldown timestamp so a stock that quickly
 * oscillates above its low and below its high can still trigger both kinds
 * of alerts within a single window.
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

        // Always update the high/low water marks first so the next tick has
        // accurate baselines, even if no alert fires this round.
        BigDecimal high = stock.getHighestPriceSeen();
        if (high == null || currentPrice.compareTo(high) > 0) {
            stock.setHighestPriceSeen(currentPrice);
            high = currentPrice;
        }
        BigDecimal low = stock.getLowestPriceSeen();
        if (low == null || currentPrice.compareTo(low) < 0) {
            stock.setLowestPriceSeen(currentPrice);
            low = currentPrice;
        }

        evaluateDrop(stock, currentPrice, high);
        evaluateRise(stock, currentPrice, low);
    }

    private void evaluateDrop(TrackedStock stock, BigDecimal current, BigDecimal high) {
        if (high == null || current.compareTo(high) >= 0) {
            return; // At or above the high — nothing to alert on.
        }
        BigDecimal dropPct = computeChangePercent(high, current);
        if (dropPct.compareTo(stock.getDropThresholdPercentage()) < 0) {
            return;
        }
        if (isInCooldown(stock.getLastDropAlertAt())) {
            log.debug("Skipping drop alert for {}: still in cooldown window", stock.getSymbol());
            return;
        }
        sendDropAlert(stock, current, high, dropPct);
        stock.setLastDropAlertAt(Instant.now());
    }

    private void evaluateRise(TrackedStock stock, BigDecimal current, BigDecimal low) {
        BigDecimal threshold = stock.getRiseThresholdPercentage();
        if (threshold == null) {
            return; // Rise tracking is opt-in per stock.
        }
        if (low == null || low.signum() <= 0 || current.compareTo(low) <= 0) {
            return; // At or below the low, or no usable baseline yet.
        }
        BigDecimal risePct = computeChangePercent(low, current);
        if (risePct.compareTo(threshold) < 0) {
            return;
        }
        if (isInCooldown(stock.getLastRiseAlertAt())) {
            log.debug("Skipping rise alert for {}: still in cooldown window", stock.getSymbol());
            return;
        }
        sendRiseAlert(stock, current, low, risePct);
        stock.setLastRiseAlertAt(Instant.now());
    }

    /** |a - b| / a * 100, always positive. */
    private BigDecimal computeChangePercent(BigDecimal baseline, BigDecimal current) {
        return current.subtract(baseline).abs()
            .divide(baseline, 6, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isInCooldown(Instant last) {
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

    private void sendRiseAlert(TrackedStock stock, BigDecimal current, BigDecimal low, BigDecimal risePct) {
        String subject = String.format("Price alert: %s up %s%%",
            stock.getSymbol(), risePct.toPlainString());
        String body = String.format(
            "%s is now $%s, up %s%% from a recent low of $%s.%n%n"
                + "You set a %s%% rise threshold for this stock.%n%n"
                + "— Stock Price Tracker",
            stock.getSymbol(),
            current.toPlainString(),
            risePct.toPlainString(),
            low.toPlainString(),
            stock.getRiseThresholdPercentage().toPlainString()
        );
        notificationService.notify(stock.getUser(), subject, body);
    }
}
