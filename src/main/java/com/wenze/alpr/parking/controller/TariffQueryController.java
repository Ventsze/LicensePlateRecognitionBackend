package com.wenze.alpr.parking.controller;

import com.wenze.alpr.parking.model.Tariff;
import com.wenze.alpr.parking.repo.TariffRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/parking/tariff")
public class TariffQueryController {

    private final TariffRepo tariffs;

    public TariffQueryController(TariffRepo tariffs) {
        this.tariffs = tariffs;
    }

    /** GET /api/parking/tariff/active
     *  返回当前 active=1 的费率（若没有，就返回 404）
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActive() {
        return tariffs.findFirstByActiveTrue()
                .<ResponseEntity<?>>map(t -> ResponseEntity.ok(toDto(t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(Tariff t) {
        return Map.of(
                "id", t.getId(),
                "name", t.getName(),
                "free_minutes", t.getFreeMinutes(),
                "round_up_min", t.getRoundUpMin(),
                "price_per_hour", safe(t.getPricePerHour())
        );
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
