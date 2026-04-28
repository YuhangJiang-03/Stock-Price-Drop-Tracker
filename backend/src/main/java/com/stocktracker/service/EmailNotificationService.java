package com.stocktracker.service;

import com.stocktracker.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email notification channel powered by Spring's {@link JavaMailSender}.
 *
 * <p>Works against any SMTP server; the README documents Gmail SMTP because
 * it's free and requires nothing more than a Gmail App Password.
 *
 * <p>Active when {@code app.notification.channel=email}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "email")
public class EmailNotificationService implements NotificationService {

    private final JavaMailSender mailSender;

    /**
     * Visible "From" address. Should match the SMTP-authenticated account when
     * using Gmail, otherwise Google rewrites it.
     */
    @Value("${app.notification.email.from}")
    private String fromAddress;

    @Override
    public void notify(User recipient, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(recipient.getEmail());
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("[NOTIFY:EMAIL] sent to {}", recipient.getEmail());
        } catch (Exception ex) {
            // Don't bubble up: a single failed alert shouldn't crash the
            // scheduler. The next tick will try again.
            log.error("[NOTIFY:EMAIL] failed to send to {}: {}",
                recipient.getEmail(), ex.getMessage());
        }
    }
}
