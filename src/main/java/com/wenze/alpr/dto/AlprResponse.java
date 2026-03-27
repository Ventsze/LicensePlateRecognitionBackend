package com.wenze.alpr.dto;

import java.util.List;

public class AlprResponse {
    public String download_url;
    public String message;
    public String error;
    public List<StatItem> stats;
}
