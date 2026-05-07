package com.stocktracker.service;

import com.stocktracker.dto.AuthResponse;
import com.stocktracker.dto.LoginRequest;
import com.stocktracker.dto.RegisterRequest;
import com.stocktracker.exception.BadRequestException;
import com.stocktracker.model.User;
import com.stocktracker.repository.UserRepository;
import com.stocktracker.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles registration & login. Keeps controllers thin and pushes domain rules
 * into one place.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
            .email(email)
            .password(passwordEncoder.encode(request.getPassword()))
            .phoneNumber(request.getPhoneNumber().trim())
            .displayName(normalizeDisplayName(request.getDisplayName()))
            .build();

        userRepository.save(user);

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, email, user.getDisplayName());
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new BadRequestException("Invalid email or password");
        }

        // Authentication only verifies credentials; we still need the row to
        // surface the (optional) display name back to the caller.
        String displayName = userRepository.findByEmail(email)
            .map(User::getDisplayName)
            .orElse(null);

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, email, displayName);
    }

    /**
     * Normalize a raw display-name input: trim whitespace and collapse blanks
     * to {@code null} so we never store an empty string.
     */
    private static String normalizeDisplayName(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
