package com.stocktracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public view of the authenticated user's profile. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private String email;
    private String phoneNumber;
    /** May be null when the user has not chosen one yet. */
    private String displayName;
}
