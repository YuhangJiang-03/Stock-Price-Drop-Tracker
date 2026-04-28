package com.stocktracker.service;

import com.stocktracker.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default notification channel: just logs to stdout. Lets you develop the rest
 * of the system without configuring Gmail / Twilio / etc.
 *
 * <p>Active when {@code app.notification.channel=log} or the property is unset.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "log", matchIfMissing = true)
public class LoggingNotificationService implements NotificationService {

    @Override
    public void notify(User recipient, String subject, String message) {
        log.info("[NOTIFY:LOG] to={} | subject=\"{}\" | body=\"{}\"",
            recipient.getEmail(), subject, message);
    }
}
