package com.wenze.alpr.parking.repo;

import com.wenze.alpr.parking.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepo extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByPlateOrderByCreatedAtDesc(String plate);
}