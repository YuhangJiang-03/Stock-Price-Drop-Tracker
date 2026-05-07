package com.stocktracker.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT-based security configuration.
 *
 * <ul>
 *   <li>Public API: {@code /api/auth/**} (register/login)</li>
 *   <li>Protected API: every other {@code /api/**} path</li>
 *   <li>Public: everything else (the bundled React SPA and its assets)</li>
 *   <li>CORS open to the React dev server (localhost:3000) by default</li>
 * </ul>
 *
 * <p>Note that static resources (the SPA shell) are intentionally permitted
 * for everyone. The auth gate lives in the React app — unauthenticated
 * visitors hit a public file, the SPA boots, and any actual data request
 * to {@code /api/**} comes back 401 unless they log in.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppUserDetailsService userDetailsService;

    /**
     * Origin patterns the browser is allowed to call this API from. The default
     * covers the React dev servers and any Cloudflare quick tunnel URL
     * ({@code *.trycloudflare.com}). Add your permanent production hostname
     * via the {@code APP_CORS_ORIGINS} env var, e.g.
     * {@code APP_CORS_ORIGINS=http://localhost:5173,https://stocks.example.com}.
     */
    @Value("${app.cors.allowed-origin-patterns}")
    private List<String> allowedOriginPatterns;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                // Anything else is a SPA asset (index.html, /assets/**, favicon...).
                // The WebConfig SPA resolver will 404 for non-existent files.
                .anyRequest().permitAll()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS for both dev (cross-origin React dev server -> backend) and prod
     * (same-origin SPA whose ES module assets still trigger CORS-mode fetches
     * from the browser, so an {@code Origin} header is sent even though the
     * SPA is bundled in the same jar).
     *
     * <p>{@link CorsConfiguration#setAllowedOriginPatterns} (rather than
     * {@code setAllowedOrigins}) is used so wildcard entries like
     * {@code https://*.trycloudflare.com} work alongside
     * {@code allowCredentials=true}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowedOriginPatterns);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
