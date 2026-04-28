package com.stocktracker.service;

/**
 * Abstraction over an SMS provider. The mock implementation logs to stdout;
 * a future {@code TwilioSmsService} can replace it transparently.
 */
public interface SmsService {

    /**
     * Send an SMS message.
     *
     * @param phoneNumber destination, in E.164 format ({@code +14155552671})
     * @param message     plain-text body, &lt;= 160 chars for a single segment
     */
    void sendSms(String phoneNumber, String message);
}
