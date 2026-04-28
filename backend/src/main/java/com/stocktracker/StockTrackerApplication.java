package com.stocktracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Stock Price Tracker backend.
 *
 * <p>{@code @EnableScheduling} activates the {@code @Scheduled} job that
 * polls stock prices and triggers SMS alerts.
 */
@SpringBootApplication
@EnableScheduling
public class StockTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockTrackerApplication.class, args);
    }
}
