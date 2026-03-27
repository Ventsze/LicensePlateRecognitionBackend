package com.wenze.alpr.parking.repo;

import com.wenze.alpr.parking.model.ParkingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import com.wenze.alpr.parking.model.ParkingSession.Status;

import java.util.List;
import java.util.Optional;

public interface ParkingSessionRepo extends JpaRepository<ParkingSession, Long> {
    Optional<ParkingSession> findFirstByPlateAndStatusOrderByEntryTimeDesc(
            String plate, ParkingSession.Status status);
    Optional<ParkingSession> findTopByPlateAndStatusOrderByEntryTimeDesc(String plate, Status status);
    Optional<ParkingSession> findTopByPlateOrderByEntryTimeDesc(String plate);
    List<ParkingSession> findAllByStatusInOrderByEntryTimeAsc(List<ParkingSession.Status> statuses);
    List<ParkingSession> findAllByPlateOrderByEntryTimeDesc(String plate);
}
