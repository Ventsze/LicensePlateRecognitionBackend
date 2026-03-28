package com.wenze.alpr.parking.model;

import jakarta.persistence.*;
import java.time.Instant; // 👉 务必确保引入了 Instant

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

    // 👉 新增：VIP 到期时间字段，映射数据库中的 vip_expire_time
    @Column(name = "vip_expire_time")
    private Instant vipExpireTime;

    // ----- 原有的 getters / setters -----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    // ----- 👉 新增的 getters / setters （解决报错的关键） -----
    public Instant getVipExpireTime() {
        return vipExpireTime;
    }

    public void setVipExpireTime(Instant vipExpireTime) {
        this.vipExpireTime = vipExpireTime;
    }
}