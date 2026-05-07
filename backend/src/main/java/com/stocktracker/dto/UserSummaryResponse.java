package com.stocktracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public-facing snapshot of another user, returned by search and profile-by-id
 * endpoints. Intentionally omits private fields like {@code email} and
 * {@code phoneNumber}. {@code displayName} is always populated — when the user
 * hasn't picked one, we fall back to their email's local-part on the server so
 * search results never look anonymous.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSummaryResponse {
    private Long id;
    private String displayName;
    private Instant joinedAt;
}
