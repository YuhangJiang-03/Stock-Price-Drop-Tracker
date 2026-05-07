package com.stocktracker.controller;

import com.stocktracker.dto.UserSummaryResponse;
import com.stocktracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public-ish lookup of <em>other</em> users — search by display name / email,
 * fetch a single user's privacy-safe profile by id. Both endpoints return
 * {@link UserSummaryResponse} so callers can never read another user's email or
 * phone number through this surface; for the caller's own profile see
 * {@link UserController} (mounted at {@code /me}).
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UsersController {

    private final UserService userService;

    /**
     * Substring search across display name + email. Returns up to 20 hits and
     * an empty list for queries shorter than 2 characters (so a single
     * keystroke doesn't drag the whole table). The caller is excluded from
     * results.
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserSummaryResponse>> search(
        @AuthenticationPrincipal UserDetails principal,
        @RequestParam(name = "q", required = false, defaultValue = "") String query
    ) {
        return ResponseEntity.ok(userService.searchUsers(principal.getUsername(), query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserSummaryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
