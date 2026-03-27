// src/main/java/com/wenze/alpr/parking/controller/SessionsQueryController.java
package com.wenze.alpr.parking.controller;

import com.wenze.alpr.parking.model.ParkingSession;
import com.wenze.alpr.parking.model.Tariff;
import com.wenze.alpr.parking.repo.ParkingSessionRepo;
import com.wenze.alpr.parking.repo.TariffRepo;
import com.wenze.alpr.parking.service.FeeCalculator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/parking/sessions")
public class SessionsQueryController {

    private final ParkingSessionRepo repo;
    private final FeeCalculator feeCalculator;
    private final TariffRepo tariffRepo;

    public SessionsQueryController(ParkingSessionRepo repo, FeeCalculator feeCalculator, TariffRepo tariffRepo) {
        this.repo = repo;
        this.feeCalculator = feeCalculator;
        this.tariffRepo = tariffRepo;
    }

    /** GET /api/parking/sessions/latest?plate=BG65USJ
     *  返回该车牌最近一次会话（用于入场后取入场时间）
     */
    @GetMapping("/latest")
    public ResponseEntity<ParkingSession> latestByPlate(@RequestParam("plate") String plate) {
        Optional<ParkingSession> sessionOpt = repo.findTopByPlateOrderByEntryTimeDesc(plate);

        if (sessionOpt.isPresent()) {
            ParkingSession session = sessionOpt.get();

            // 👉 核心逻辑：如果车辆还在场，则实时计算费用
            if (session.getStatus() != ParkingSession.Status.CLOSED) {
                // 获取该会话关联的费率配置
                Optional<Tariff> tariffOpt = tariffRepo.findById(session.getTariffId());
                if (tariffOpt.isPresent()) {
                    // 使用 FeeCalculator 计算从入场到当前的费用
                    // 入场时间：session.getEntryTime()，当前时间：Instant.now()
                    session.setFee(feeCalculator.calc(session.getEntryTime(), Instant.now(), tariffOpt.get()));
                }
            }
            return ResponseEntity.ok(session);
        }

        return ResponseEntity.notFound().build();
    }

    /** GET /api/parking/sessions/{id}
     *  按ID返回完整会话（用于出场后展示入/出时间与费用）
     */
    @GetMapping("/{id}")
    public ResponseEntity<ParkingSession> getById(@PathVariable("id") Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 👉 新增：模拟支付结算接口
    @PostMapping("/checkout")
    public ResponseEntity<?> checkoutByPlate(@RequestParam("plate") String plate) {
        Optional<ParkingSession> sessionOpt = repo.findTopByPlateOrderByEntryTimeDesc(plate);

        if (sessionOpt.isPresent()) {
            ParkingSession session = sessionOpt.get();
            // 如果车辆还在场，执行结算离场逻辑
            if (session.getStatus() != ParkingSession.Status.CLOSED) {
                session.setStatus(ParkingSession.Status.CLOSED);
                session.setExitTime(Instant.now());

                // 可选：在这里你也可以再调用一次 feeCalculator 算出最终精确费用

                repo.save(session);
                return ResponseEntity.ok(session); // 返回更新后的订单
            }
        }
        return ResponseEntity.badRequest().body("未找到在场记录或已结算");
    }

    /** * 👉 新增：GET /api/parking/sessions/all?plate=xxx
     * 返回该车牌的所有停车历史
     */
    @GetMapping("/all")
    public ResponseEntity<List<ParkingSession>> getAllByPlate(@RequestParam("plate") String plate) {
        return ResponseEntity.ok(repo.findAllByPlateOrderByEntryTimeDesc(plate));
    }
}
