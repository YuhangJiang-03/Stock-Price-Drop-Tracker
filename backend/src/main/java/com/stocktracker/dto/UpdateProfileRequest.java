package com.stocktracker.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload for {@code PATCH /me}. Currently only the display name is editable;
 * keep one DTO so future fields (avatar, locale, …) slot in cleanly.
 *
 * <p>An explicit empty/blank string is interpreted as "clear the value" so the
 * UI can fall back to the email-local-part again.</p>
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 60, message = "Display name must be 60 characters or fewer")
    private String displayName;
}
