package com.wenze.alpr.parking.controller;

import com.wenze.alpr.parking.service.GateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/parking")
public class GatesController {
    private final GateService gate;
    public GatesController(GateService gate) { this.gate = gate; }

    @PostMapping("/entry/trigger")
    public ResponseEntity<?> entry(@RequestParam("cameraId") Long cameraId) {
        try {
            Map<String,Object> ok = gate.entryTrigger(cameraId);
            return ResponseEntity.ok(ok);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage()));
        }
    }

    @PostMapping("/exit/trigger")
    public ResponseEntity<?> exit(@RequestParam("cameraId") Long cameraId) {
        try {
            Map<String,Object> ok = gate.exitTrigger(cameraId);
            return ResponseEntity.ok(ok);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage()));
        }
    }

    // 诊断用：快速验证映射是否生效
    @GetMapping("/ping")
    public String ping() { return "pong"; }
}
