package com.wenze.alpr.parking.service;

import com.wenze.alpr.parking.model.ParkingSession;
import com.wenze.alpr.parking.model.Tariff;
import com.wenze.alpr.parking.model.Vehicle;
import com.wenze.alpr.parking.repo.ParkingSessionRepo;
import com.wenze.alpr.parking.repo.TariffRepo;
import com.wenze.alpr.parking.repo.VehicleRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class GateService {
    private final ParkingSessionRepo sessions;
    private final VehicleRepo vehicles;
    private final TariffRepo tariffs;
    private final DetectionClient detector;
    private final FeeCalculator feeCalc;

    public GateService(ParkingSessionRepo sessions, VehicleRepo vehicles, TariffRepo tariffs,
                       DetectionClient detector, FeeCalculator feeCalc) {
        this.sessions = sessions; this.vehicles = vehicles; this.tariffs = tariffs;
        this.detector = detector; this.feeCalc = feeCalc;
    }

    @Transactional
    public Map<String, Object> entryTrigger(Long cameraId) {
        // 1) 识别并规范出不可变的 finalPlate
        String p = detector.detect(cameraId).getPlate();
        if (p == null || p.isBlank()) p = "TEST123";
        final String finalPlate = p;

        // 2) 查是否已有 OPEN 会话
        Optional<ParkingSession> openOpt =
                sessions.findFirstByPlateAndStatusOrderByEntryTimeDesc(finalPlate, ParkingSession.Status.OPEN);

        if (openOpt.isEmpty()) {
            // 3) 取计费规则（无则创建一条默认）
            Tariff t = tariffs.findFirstByActiveTrue().orElse(null);
            if (t == null) {
                t = new Tariff(); // 使用实体里的默认字段值
                t = tariffs.save(t);
            }
            // 4) 新建会话
            ParkingSession s = new ParkingSession();
            s.setPlate(finalPlate);
            s.setEntryTime(Instant.now());
            // 如需记录闸机：s.setEntryGateId(cameraId);
            s.setEntryGateId(null);
            s.setTariffId(t.getId());
            s.setStatus(ParkingSession.Status.OPEN);
            sessions.save(s);
        }

        boolean whitelist = isWhitelist(finalPlate);

        return Map.of(
                "plate", finalPlate,
                "isWhitelist", whitelist,
                "action", whitelist ? "OPEN_GATE" : "MANUAL"
        );
    }

    @Transactional
    public Map<String, Object> exitTrigger(Long cameraId) {
        String p = detector.detect(cameraId).getPlate();
        if (p == null || p.isBlank()) p = "TEST123";
        final String finalPlate = p;

        // 1) 先找“最近的 OPEN 会话”
        Optional<ParkingSession> openOpt =
                sessions.findFirstByPlateAndStatusOrderByEntryTimeDesc(finalPlate, ParkingSession.Status.OPEN);

        if (openOpt.isEmpty()) {
            // 2) 没有 OPEN：看“最近一次会话”是否已结算（幂等返回）
            Optional<ParkingSession> lastOpt = sessions.findTopByPlateOrderByEntryTimeDesc(finalPlate);
            if (lastOpt.isPresent() && lastOpt.get().getExitTime() != null) {
                ParkingSession s = lastOpt.get();
                boolean whitelist = isWhitelist(finalPlate);
                return Map.of(
                        "sessionId", s.getId(),
                        "plate", finalPlate,
                        "fee", s.getFee() == null ? BigDecimal.ZERO : s.getFee(),
                        "whitelist", whitelist,
                        "entryTime", s.getEntryTime(),
                        "exitTime", s.getExitTime(),
                        "action", "ALREADY_CLOSED"
                );
            }
            // 3) 真的没有任何可用会话 → 返回 409（业务冲突），而不是 500
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No OPEN session for plate: " + finalPlate);
        }

        // 4) 正常结算分支（再防一手并发：如果此时已被其他请求结算，就按 ALREADY_CLOSED 返回）
        ParkingSession s = openOpt.get();
        if (s.getExitTime() != null || s.getStatus() != ParkingSession.Status.OPEN) {
            boolean whitelist = isWhitelist(finalPlate);
            return Map.of(
                    "sessionId", s.getId(),
                    "plate", finalPlate,
                    "fee", s.getFee() == null ? BigDecimal.ZERO : s.getFee(),
                    "whitelist", whitelist,
                    "entryTime", s.getEntryTime(),
                    "exitTime", s.getExitTime(),
                    "action", "ALREADY_CLOSED"
            );
        }

        // === 计算费用 ===
        s.setExitTime(Instant.now());
        // 如需记录闸机：s.setExitGateId(cameraId);
        s.setExitGateId(null);

        Tariff t = tariffs.findById(s.getTariffId()).orElseThrow();
        BigDecimal fee = feeCalc.calc(s.getEntryTime(), s.getExitTime(), t);
        s.setFee(fee);

        boolean whitelist = isWhitelist(finalPlate);
        s.setStatus(whitelist ? ParkingSession.Status.CLOSED : ParkingSession.Status.READY_TO_CLOSE);
        sessions.save(s);

        return Map.of(
                "sessionId", s.getId(),
                "plate", finalPlate,
                "fee", fee,
                "whitelist", whitelist,
                "entryTime", s.getEntryTime(),
                "exitTime", s.getExitTime(),
                "action", whitelist ? "OPEN_GATE" : "WAIT_PAYMENT"
        );
    }

    private boolean isWhitelist(String plate) {
        return vehicles.findByPlate(plate)
                .map(v -> v.getType() == Vehicle.Type.whitelist)
                .orElse(false);
    }
}
