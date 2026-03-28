package com.wenze.alpr.parking.service;

import com.wenze.alpr.parking.model.Tariff;
import com.wenze.alpr.parking.model.Vehicle;
import com.wenze.alpr.parking.repo.VehicleRepo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class FeeCalculator {

    private final VehicleRepo vehicleRepo;

    // 注入 VehicleRepo，用于查询车辆是否为 VIP
    public FeeCalculator(VehicleRepo vehicleRepo) {
        this.vehicleRepo = vehicleRepo;
    }

    public BigDecimal calc(String plate, Instant entry, Instant exit, Tariff t) {
        // 1. 查询车辆信息
        Optional<Vehicle> vehicleOpt = vehicleRepo.findByPlate(plate);

        if (vehicleOpt.isPresent()) {
            Vehicle v = vehicleOpt.get();
            // 2. 核心逻辑：判断是否为白名单/VIP
            if (v.getType() == Vehicle.Type.whitelist) {
                // 3. 检查 VIP 是否有效：
                // 如果 vipExpireTime 是 null（代表永久），或者到期时间在当前出场时间之后，则免单
                if (v.getVipExpireTime() == null || v.getVipExpireTime().isAfter(exit)) {
                    return BigDecimal.ZERO;
                }
            }
        }

        // 4. 普通车辆或已过期的 VIP，执行正常的计费逻辑
        if (t == null) {
            return BigDecimal.ZERO; // 防止费率未配置时报错
        }

        long total = Duration.between(entry, exit).toMinutes();

        long freeMin = t.getFreeMinutes();

        int roundUp = t.getRoundUpMin() > 0 ? t.getRoundUpMin() : 60;

        // pricePerHour 是 BigDecimal 对象，它有可能是 null，所以保留判空
        BigDecimal price = t.getPricePerHour() != null ? t.getPricePerHour() : BigDecimal.ZERO;

        long eff = Math.max(0, total - freeMin);
        long rounded = (long) Math.ceil(eff / (double) roundUp) * roundUp;

        return BigDecimal.valueOf(rounded)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.UP)
                .multiply(price);
    }
}