package com.wenze.alpr.parking.controller;

import com.wenze.alpr.parking.model.ParkingSession;
import com.wenze.alpr.parking.repo.ParkingSessionRepo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ParkingSessionRepo sessions;

    public DashboardController(ParkingSessionRepo sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        // 1. 获取“今天凌晨 0 点”的时间戳
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);

        // 2. 统计当前在场车辆与空余车位（假设车位总数是 200）
        int totalCapacity = 200;
        long openCount = sessions.findAllByStatusInOrderByEntryTimeAsc(List.of(ParkingSession.Status.OPEN)).size();
        long availableSpots = Math.max(0, totalCapacity - openCount);

        // 3. 获取所有“今天入场”的记录
        List<ParkingSession> todaySessions = sessions.findAll().stream()
                .filter(s -> s.getEntryTime().isAfter(startOfToday))
                .collect(Collectors.toList());

        // 4. 统计“今天离场”的总收入
        BigDecimal todayRevenue = sessions.findAll().stream()
                .filter(s -> s.getExitTime() != null && s.getExitTime().isAfter(startOfToday))
                .map(s -> s.getFee() != null ? s.getFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. 核心算法：按小时统计今天每个时间段的进场车次 (用于折线图)
        int[] hourlyCounts = new int[24];
        for (ParkingSession s : todaySessions) {
            // 把 UTC 时间转换为系统本地时区的小时 (0-23)
            int hour = s.getEntryTime().atZone(ZoneId.systemDefault()).getHour();
            hourlyCounts[hour]++;
        }

        // 把数据包装成 ECharts 喜欢的前端格式
        List<String> hours = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        // 为了图表好看，我们截取 08:00 到 20:00 的数据（或者你也可以全部展示 00:00 - 23:00）
        for (int i = 8; i <= 20; i++) {
            hours.add(String.format("%02d:00", i));
            counts.add(hourlyCounts[i]);
        }

        // 6. 打包返回给前端
        Map<String, Object> res = new HashMap<>();
        res.put("revenue", todayRevenue);
        res.put("availableSpots", availableSpots);
        res.put("totalCapacity", totalCapacity);
        res.put("todayEntries", todaySessions.size());
        res.put("chartCategories", hours); // X轴：时间段
        res.put("chartData", counts);      // Y轴：车流量

        return res;
    }
}