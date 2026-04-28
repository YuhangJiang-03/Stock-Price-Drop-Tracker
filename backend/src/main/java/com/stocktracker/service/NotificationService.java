package com.stocktracker.service;

import com.stocktracker.model.User;

/**
 * Channel-agnostic notification abstraction. Implementations decide how to
 * actually deliver the message (log line, email, SMS, push, ...). The whole
 * {@link User} is passed in so the implementation can choose the right
 * recipient field (email vs phone number) without the caller having to know.
 *
 * <p>The active implementation is selected via {@code app.notification.channel}
 * in {@code application.yml}:
 * <ul>
 *   <li>{@code log}   → {@link LoggingNotificationService} (default)</li>
 *   <li>{@code email} → {@link EmailNotificationService} (free via Gmail SMTP)</li>
 * </ul>
 */
public interface NotificationService {

    /**
     * Send a notification to the given user.
     *
     * @param recipient the user to notify
     * @param subject   short headline (used as the email subject; ignored by SMS)
     * @param message   plain-text body
     */
    void notify(User recipient, String subject, String message);
}
