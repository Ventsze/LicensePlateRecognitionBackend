package com.wenze.alpr.parking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "parking_sessions")
public class ParkingSession {

    public enum Status { OPEN, READY_TO_CLOSE, CLOSED, EXCEPTION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String plate;

    @Column(nullable = false)
    private Instant entryTime;

    private Instant exitTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    private Long entryGateId;
    private Long exitGateId;
    private Long tariffId;
    private BigDecimal fee;

    @Lob
    private String imagesJson;

    @Lob
    private String metaJson;

    // getters / setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public Instant getEntryTime() { return entryTime; }
    public void setEntryTime(Instant entryTime) { this.entryTime = entryTime; }
    public Instant getExitTime() { return exitTime; }
    public void setExitTime(Instant exitTime) { this.exitTime = exitTime; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Long getEntryGateId() { return entryGateId; }
    public void setEntryGateId(Long entryGateId) { this.entryGateId = entryGateId; }
    public Long getExitGateId() { return exitGateId; }
    public void setExitGateId(Long exitGateId) { this.exitGateId = exitGateId; }
    public Long getTariffId() { return tariffId; }
    public void setTariffId(Long tariffId) { this.tariffId = tariffId; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public String getImagesJson() { return imagesJson; }
    public void setImagesJson(String imagesJson) { this.imagesJson = imagesJson; }
    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
}
