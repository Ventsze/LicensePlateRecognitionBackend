package com.wenze.alpr.parking.repo;

import com.wenze.alpr.parking.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepo extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByPlate(String plate);

    // 列出所有白名单
    List<Vehicle> findAllByType(Vehicle.Type type);
}
