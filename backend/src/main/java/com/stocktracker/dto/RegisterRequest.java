package com.stocktracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload for {@code POST /auth/register}. */
@Data
public class RegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    private String password;

    /**
     * Loose E.164-ish check; full validation should be delegated to a real
     * library (e.g. libphonenumber) in production.
     */
    @NotBlank
    @Pattern(
        regexp = "^\\+?[0-9 .()-]{7,20}$",
        message = "Phone number must be a valid international number"
    )
    private String phoneNumber;
}
