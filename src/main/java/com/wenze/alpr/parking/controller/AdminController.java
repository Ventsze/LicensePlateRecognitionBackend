// src/main/java/com/wenze/alpr/parking/controller/AdminController.java
package com.wenze.alpr.parking.controller;

import com.wenze.alpr.parking.model.ParkingSession;
import com.wenze.alpr.parking.model.Tariff;
import com.wenze.alpr.parking.model.Vehicle;
import com.wenze.alpr.parking.repo.ParkingSessionRepo;
import com.wenze.alpr.parking.repo.TariffRepo;
import com.wenze.alpr.parking.repo.VehicleRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wenze.alpr.parking.service.FeeCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parking/admin")
public class AdminController {

    private final VehicleRepo vehicles;
    private final ParkingSessionRepo sessions;
    private final TariffRepo tariffs;
    private final FeeCalculator feeCalculator;

    public AdminController(VehicleRepo vehicles, ParkingSessionRepo sessions, TariffRepo tariffs, FeeCalculator feeCalculator) {
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.tariffs = tariffs;
        this.feeCalculator = feeCalculator;
    }
    // ========== 白名单 ==========

    @GetMapping("/whitelist")
    public List<Map<String, Object>> listWhitelist() {
        return vehicles.findAllByType(Vehicle.Type.whitelist).stream()
                .sorted(Comparator.comparing(Vehicle::getPlate))
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("plate", v.getPlate());
                    // 👉 新增：将到期时间返回给前端
                    m.put("vipExpireTime", v.getVipExpireTime());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ========== 添加白名单 ==========
    @PostMapping("/whitelist")
    public ResponseEntity<?> addWhitelist(@RequestBody Map<String, Object> body) { // 👉 注意这里改成了 Object
        String plate = (String) body.get("plate");
        if (plate == null || plate.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(simpleErr("车牌号不能为空"));
        }

        // 👉 新增：解析前端传来的 days，如果没有传，默认 30 天
        int days = 30;
        if (body.containsKey("days")) {
            Object daysObj = body.get("days");
            if (daysObj instanceof Number) {
                days = ((Number) daysObj).intValue();
            } else if (daysObj instanceof String) {
                days = Integer.parseInt((String) daysObj);
            }
        }

        // 查找数据库里是否已经有这辆车，如果没有就新建一个
        Vehicle vehicle = vehicles.findByPlate(plate).orElse(new Vehicle());
        vehicle.setPlate(plate);
        vehicle.setType(Vehicle.Type.whitelist); // 标记为白名单/VIP

        // 👉 新增：计算并设置到期时间
        if (days >= 9999) {
            // 前端传 9999 代表永久，我们把到期时间设为 null
            vehicle.setVipExpireTime(null);
        } else {
            // 当前时间 + 购买的天数
            vehicle.setVipExpireTime(Instant.now().plus(days, ChronoUnit.DAYS));
        }

        vehicles.save(vehicle);

        return ResponseEntity.ok(simpleOk());
    }

    // ========== 人工干预出场 ==========
    @PostMapping("/manual-exit/{sessionId}")
    public ResponseEntity<?> manualExit(@PathVariable("sessionId") Long sessionId){
        // 1. 查找订单
        Optional<ParkingSession> opt = sessions.findById(sessionId);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(simpleErr("找不到该停车记录"));
        }

        ParkingSession session = opt.get();
        if (session.getStatus() != ParkingSession.Status.OPEN) {
            return ResponseEntity.badRequest().body(simpleErr("该车辆不在车库中或已结算"));
        }

        // 2. 模拟车辆现在出场
        Instant now = Instant.now();
        session.setExitTime(now);

        // 3. 判断是否为白名单
        boolean isWhitelist = vehicles.findByPlate(session.getPlate())
                .map(v -> v.getType() == Vehicle.Type.whitelist)
                .orElse(false);

        // 4. 计算费用
        Tariff t = tariffs.findFirstByActiveTrue().orElse(null);
        BigDecimal fee = feeCalculator.calc(session.getPlate(), session.getEntryTime(), now, t);

        // 5. 更新状态并保存
        session.setFee(fee);
        session.setStatus(ParkingSession.Status.READY_TO_CLOSE);
        sessions.save(session);

        return ResponseEntity.ok(simpleOk());
    }

    // ========== 人工干预入场 ==========
    @PostMapping("/manual-entry")
    public ResponseEntity<?> manualEntry(@RequestBody Map<String, String> body) {
        String plate = body.get("plate");
        if (plate == null || plate.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(simpleErr("车牌号不能为空"));
        }
        plate = plate.trim().toUpperCase();

        // 1. 检查该车牌是否已经在库中 (避免重复入库)
        String finalPlate = plate;
        boolean isAlreadyIn = sessions.findAllByStatusInOrderByEntryTimeAsc(List.of(ParkingSession.Status.OPEN))
                .stream()
                .anyMatch(s -> s.getPlate().equals(finalPlate));

        if (isAlreadyIn) {
            return ResponseEntity.badRequest().body(simpleErr("该车辆已经在库中，无法重复入库"));
        }

        // 2. 创建一条新的在库记录
        ParkingSession session = new ParkingSession();
        session.setPlate(plate);
        session.setEntryTime(Instant.now());
        session.setStatus(ParkingSession.Status.OPEN);
        sessions.save(session);

        return ResponseEntity.ok(simpleOk());
    }

    @DeleteMapping("/whitelist/{plate}")
    public ResponseEntity<?> removeWhitelist(@PathVariable("plate") String plate) {
        return vehicles.findByPlate(plate).map(v -> {
            v.setType(Vehicle.Type.normal); // 或者直接 vehicles.delete(v);
            vehicles.save(v);
            return ResponseEntity.ok(simpleOk());
        }).orElse(ResponseEntity.notFound().build());
    }

    // ========== 当前在场 ==========

    @GetMapping("/parked")
    public List<Map<String, Object>> listParked() {
        List<ParkingSession.Status> inYard = List.of(
                ParkingSession.Status.OPEN,
                ParkingSession.Status.READY_TO_CLOSE
        );
        Tariff t = tariffs.findFirstByActiveTrue().orElse(null);
        Instant now = Instant.now();

        return sessions.findAllByStatusInOrderByEntryTimeAsc(inYard).stream()
                .map(s -> {
                    // ✅ 关键点：READY_TO_CLOSE 用 exitTime 固定时长；OPEN 才用 now
                    Instant end = (s.getExitTime() != null) ? s.getExitTime() : now;
                    long minutes = Math.max(0, java.time.Duration.between(s.getEntryTime(), end).toMinutes());

                    // ✅ 关键点：READY_TO_CLOSE 显示最终 fee；OPEN 才按分钟估算
                    java.math.BigDecimal feeEst;
                    if (s.getStatus() == ParkingSession.Status.READY_TO_CLOSE && s.getFee() != null) {
                        feeEst = s.getFee();
                    } else {
                        feeEst = estimateFee(minutes, t); // 你原有的估算方法
                    }

                    boolean isWhitelist = vehicles.findByPlate(s.getPlate())
                            .map(v -> v.getType() == com.wenze.alpr.parking.model.Vehicle.Type.whitelist)
                            .orElse(false);

                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("sessionId", s.getId());
                    m.put("plate", s.getPlate());
                    m.put("entryTime", s.getEntryTime());
                    m.put("exitTime", s.getExitTime());      // ✅ 额外返回，前端如需展示可用
                    m.put("minutes", minutes);               // ✅ 已按 exitTime/now 固定
                    m.put("estimatedFee", feeEst);           // ✅ READY_TO_CLOSE 时为最终费用
                    m.put("finalFee", s.getFee());           // （可选）返回已结算费
                    m.put("whitelist", isWhitelist);
                    m.put("status", s.getStatus().name());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
    }


    // 费用估算（与实际结算保持同一规则）
    private BigDecimal estimateFee(long totalMinutes, Tariff t) {
        if (t == null) return BigDecimal.ZERO;
        int free = Optional.ofNullable(t.getFreeMinutes()).orElse(0);
        int step = Optional.ofNullable(t.getRoundUpMin()).orElse(60);
        BigDecimal perHour = Optional.ofNullable(t.getPricePerHour()).orElse(BigDecimal.ZERO);

        long afterFree = Math.max(0, totalMinutes - free);
        long chargeMin = ((afterFree + step - 1) / step) * step;
        return BigDecimal.valueOf(chargeMin)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.UP)
                .multiply(perHour);
    }

    // ---------- 小工具 ----------
    private Map<String, Object> simpleOk() {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return m;
    }

    private Map<String, Object> simpleErr(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }
}
