package com.stocktracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A stock symbol a user is watching, together with the highest price seen so
 * far and the percentage drop that should trigger an SMS alert.
 *
 * <p>The {@code (user_id, symbol)} pair is unique so a user cannot accidentally
 * track the same symbol twice.
 */
@Entity
@Table(
    name = "tracked_stocks",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_tracked_stocks_user_symbol",
        columnNames = {"user_id", "symbol"}
    ),
    indexes = @Index(name = "idx_tracked_stocks_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning user. Loaded lazily so listing all tracked stocks does not pull
     * the user record on every row.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String symbol;

    /**
     * Drop threshold in percent (e.g. 5.0 for 5%). Stored as BigDecimal to
     * avoid floating-point surprises when comparing against price changes.
     */
    @Column(name = "drop_threshold_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal dropThresholdPercentage;

    /**
     * Optional rise threshold in percent. When set, an alert fires whenever
     * the price climbs at least this much above {@code lowestPriceSeen}.
     * Nullable so existing rows (and users who don't care about rises)
     * remain valid without a data backfill.
     */
    @Column(name = "rise_threshold_percentage", precision = 6, scale = 2)
    private BigDecimal riseThresholdPercentage;

    /**
     * Highest price observed since this stock was added (or since the last
     * alert reset, depending on policy). Used as the reference point for
     * drop alerts.
     */
    @Column(name = "highest_price_seen", precision = 19, scale = 4)
    private BigDecimal highestPriceSeen;

    /**
     * Lowest price observed since this stock was added; the reference point
     * for rise alerts.
     */
    @Column(name = "lowest_price_seen", precision = 19, scale = 4)
    private BigDecimal lowestPriceSeen;

    /**
     * Last time we sent a drop alert; used to enforce the per-direction
     * cool-down. The column name is preserved for backwards compatibility
     * with rows created before rise tracking was added.
     */
    @Column(name = "last_notified_at")
    private Instant lastDropAlertAt;

    /** Last time we sent a rise alert; used to enforce the cool-down. */
    @Column(name = "last_rise_alert_at")
    private Instant lastRiseAlertAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
