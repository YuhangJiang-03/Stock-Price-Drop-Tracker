package com.stocktracker.repository;

import com.stocktracker.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Case-insensitive substring match on the display name OR email. The query
     * is already lower-cased and wrapped with {@code %} by the caller — this
     * keeps the SQL simple and lets Postgres use a trigram / btree index on
     * those columns later if we add one.
     *
     * <p>Callers should exclude the requesting user via {@code excludeId} so
     * search results never include "yourself".
     */
    // The explicit ESCAPE '\' lets the service layer escape user-supplied
    // wildcards (`%`, `_`) so a stray "%" doesn't match every row.
    @Query(
        "SELECT u FROM User u " +
        "WHERE u.id <> :excludeId " +
        "  AND (LOWER(COALESCE(u.displayName, '')) LIKE :pattern ESCAPE '\\' " +
        "       OR LOWER(u.email) LIKE :pattern ESCAPE '\\') " +
        "ORDER BY " +
        // Prefer users whose display name actually matches over email-only
        // matches so the most "human" hits surface first.
        "  CASE WHEN LOWER(COALESCE(u.displayName, '')) LIKE :pattern ESCAPE '\\' THEN 0 ELSE 1 END, " +
        "  u.displayName ASC NULLS LAST, " +
        "  u.email ASC"
    )
    List<User> searchUsers(
        @Param("pattern") String pattern,
        @Param("excludeId") Long excludeId,
        Pageable pageable
    );
}
