package com.wenze.alpr.parking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tariffs")
public class Tariff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name = "default";

    @Column(nullable = false)
    private int freeMinutes = 30;

    @Column(nullable = false)
    private int roundUpMin = 30;

    @Column(nullable = false)
    private BigDecimal pricePerHour = new BigDecimal("40.00");

    @Column(nullable = false)
    private boolean active = true;

    // getters / setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getFreeMinutes() { return freeMinutes; }
    public void setFreeMinutes(int freeMinutes) { this.freeMinutes = freeMinutes; }
    public int getRoundUpMin() { return roundUpMin; }
    public void setRoundUpMin(int roundUpMin) { this.roundUpMin = roundUpMin; }
    public BigDecimal getPricePerHour() { return pricePerHour; }
    public void setPricePerHour(BigDecimal pricePerHour) { this.pricePerHour = pricePerHour; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
