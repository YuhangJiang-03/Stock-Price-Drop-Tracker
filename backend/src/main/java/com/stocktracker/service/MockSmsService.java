package com.stocktracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mock SMS service: logs the message instead of sending it. Active by default
 * (when {@code app.sms.provider=mock}, or the property is absent).
 *
 * <p>To wire Twilio later, drop a {@code TwilioSmsService} bean and toggle the
 * property to {@code twilio} — no other code needs to change.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsService implements SmsService {

    @Override
    public void sendSms(String phoneNumber, String message) {
        log.info("[MOCK SMS] to={} | message=\"{}\"", phoneNumber, message);
    }
}
