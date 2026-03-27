// src/main/java/com/wenze/alpr/controller/AlprController.java
package com.wenze.alpr.controller;

import com.wenze.alpr.dto.AlprResponse;
import com.wenze.alpr.dto.StatItem;
import com.wenze.alpr.service.PythonPipelineService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AlprController {

    private final PythonPipelineService pipeline;

    public AlprController(PythonPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping(value = "/alpr/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AlprResponse upload(@RequestParam("file") MultipartFile file) {
        return pipeline.process(file);
    }

    // 兜底统计接口：前端 fallback 使用
    @GetMapping("/alpr/stats")
    public Map<String, Object> stats(@RequestParam(value = "run", required = false) String run) {
        try {
            List<StatItem> s = pipeline.computeLatestStats(); // 新增一个包装方法，内部与 process 同源逻辑
            return Map.of("stats", s);
        } catch (Exception e) {
            return Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage(),
                    "stats", List.of());
        }
    }
}
