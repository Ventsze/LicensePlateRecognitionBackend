package com.wenze.alpr.parking.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    public enum Type { normal, whitelist }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String plate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type = Type.normal;

    // getters / setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
}
