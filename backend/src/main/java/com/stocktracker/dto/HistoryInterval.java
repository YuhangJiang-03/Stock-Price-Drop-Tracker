package com.stocktracker.dto;

import java.time.Duration;

/**
 * Time window the user is asking to graph, plus the bucket size we use to
 * sample it. The number of points per interval is kept small enough that the
 * chart stays readable but dense enough that the curve looks plausibly real.
 */
public enum HistoryInterval {

    DAY(96, Duration.ofMinutes(15)),       // ~1 day @ 15-min ticks
    WEEK(84, Duration.ofHours(2)),         // 1 week @ 2-hour ticks
    MONTH(30, Duration.ofDays(1)),         // 30 daily closes
    YEAR(52, Duration.ofDays(7));          // 52 weekly closes

    private final int points;
    private final Duration step;

    HistoryInterval(int points, Duration step) {
        this.points = points;
        this.step = step;
    }

    public int points() {
        return points;
    }

    public Duration step() {
        return step;
    }

    /** Total span covered by the interval ({@code points × step}). */
    public Duration span() {
        return step.multipliedBy(points - 1L);
    }
}
