package com.wenze.alpr.parking.repo;

import com.wenze.alpr.parking.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepo extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByPlate(String plate);
}