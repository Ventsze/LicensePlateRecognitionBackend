package com.wenze.alpr.parking.service;

import com.wenze.alpr.parking.model.Tariff;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Component
public class FeeCalculator {
    public BigDecimal calc(Instant entry, Instant exit, Tariff t) {
        long total = Duration.between(entry, exit).toMinutes();
        long eff = Math.max(0, total - t.getFreeMinutes());
        long rounded = (long) Math.ceil(eff / (double) t.getRoundUpMin()) * t.getRoundUpMin();
        return BigDecimal.valueOf(rounded)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.UP)
                .multiply(t.getPricePerHour());
    }
}
