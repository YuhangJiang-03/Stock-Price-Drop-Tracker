package com.stocktracker.service;

import com.stocktracker.dto.UpdateProfileRequest;
import com.stocktracker.dto.UserProfileResponse;
import com.stocktracker.dto.UserSummaryResponse;
import com.stocktracker.exception.NotFoundException;
import com.stocktracker.model.User;
import com.stocktracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read & mutate the authenticated caller's own profile, plus public lookups for
 * other users (search by name/email, fetch by id). Anything that touches the
 * caller's own row goes via email (taken from the JWT principal); other-user
 * lookups go via id and only return the privacy-safe {@link UserSummaryResponse}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /** Cap search results so a single keystroke can't drag the whole table. */
    private static final int SEARCH_LIMIT = 20;

    /**
     * Don't fire a search until the user has typed something meaningful — a
     * single character would match almost everything and is useless noise.
     */
    private static final int MIN_QUERY_LENGTH = 2;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        return toProfile(loadUser(email));
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = loadUser(email);
        // We treat blank/whitespace as "clear it" so the UI can revert to the
        // email-local-part fallback. Anything else gets trimmed and stored.
        user.setDisplayName(normalizeDisplayName(request.getDisplayName()));
        userRepository.save(user);
        return toProfile(user);
    }

    /**
     * Substring search over display name + email. Returns an empty list for
     * queries shorter than {@link #MIN_QUERY_LENGTH} so the client doesn't have
     * to special-case the "nothing typed yet" state.
     */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> searchUsers(String requesterEmail, String rawQuery) {
        String trimmed = rawQuery == null ? "" : rawQuery.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) return List.of();

        User requester = loadUser(requesterEmail);
        // Lowercase + escape LIKE wildcards so a user can't bypass the limit by
        // pasting "%" and matching every row in the table.
        String pattern = "%" + escapeLike(trimmed.toLowerCase()) + "%";

        return userRepository
            .searchUsers(pattern, requester.getId(), PageRequest.of(0, SEARCH_LIMIT))
            .stream()
            .map(UserService::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found: " + id));
        return toSummary(user);
    }

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }

    private static UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
            .email(user.getEmail())
            .phoneNumber(user.getPhoneNumber())
            .displayName(user.getDisplayName())
            .build();
    }

    private static UserSummaryResponse toSummary(User user) {
        return UserSummaryResponse.builder()
            .id(user.getId())
            .displayName(publicDisplayName(user))
            .joinedAt(user.getCreatedAt())
            .build();
    }

    /**
     * What other users see. Prefer the user-chosen display name; otherwise fall
     * back to the email-local-part (the same fallback the navbar uses for the
     * caller's own greeting), so a user without a display name still has a
     * readable handle in search results.
     */
    private static String publicDisplayName(User user) {
        String chosen = user.getDisplayName();
        if (chosen != null && !chosen.isBlank()) return chosen;
        String email = user.getEmail();
        int at = email == null ? -1 : email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String normalizeDisplayName(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Escape SQL LIKE metacharacters so user input can only match literally.
     * (We don't add an explicit ESCAPE clause; Hibernate's default backslash
     * escape handles these.)
     */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
