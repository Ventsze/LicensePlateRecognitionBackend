package com.wenze.alpr.parking.bootstrap;

import com.wenze.alpr.parking.model.Tariff;
import com.wenze.alpr.parking.repo.TariffRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedConfig {
    @Bean
    CommandLineRunner seedTariff(TariffRepo tariffs) {
        return args -> {
            if (tariffs.findFirstByActiveTrue().isEmpty()) {
                tariffs.save(new Tariff()); // 30分钟免费、30分钟进位、每小时40卢布
            }
        };
    }
}
