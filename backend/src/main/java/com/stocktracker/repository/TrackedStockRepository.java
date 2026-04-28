package com.stocktracker.repository;

import com.stocktracker.model.TrackedStock;
import com.stocktracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedStockRepository extends JpaRepository<TrackedStock, Long> {

    List<TrackedStock> findAllByUser(User user);

    Optional<TrackedStock> findByIdAndUser(Long id, User user);

    boolean existsByUserAndSymbolIgnoreCase(User user, String symbol);
}
