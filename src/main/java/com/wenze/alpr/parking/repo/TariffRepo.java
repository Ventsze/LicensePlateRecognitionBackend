package com.wenze.alpr.parking.repo;

import com.wenze.alpr.parking.model.Tariff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TariffRepo extends JpaRepository<Tariff, Long> {
    Optional<Tariff> findFirstByActiveTrue();
}
