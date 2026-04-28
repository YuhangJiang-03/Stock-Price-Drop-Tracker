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
            .build();

        userRepository.save(user);

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, email);
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

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, email);
    }
}
