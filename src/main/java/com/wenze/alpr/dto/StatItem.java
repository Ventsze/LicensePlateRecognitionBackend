// src/main/java/com/wenze/alpr/dto/StatItem.java
package com.wenze.alpr.dto;

public class StatItem {
    public String license_number;
    public Double max_text_score;
    public Integer samples;

    public StatItem() {}
    public StatItem(String lp, Double maxScore, Integer samples) {
        this.license_number = lp;
        this.max_text_score = maxScore;
        this.samples = samples;
    }
}
