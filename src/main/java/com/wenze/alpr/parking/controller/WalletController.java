package com.wenze.alpr.parking.controller;

import com.wenze.alpr.parking.service.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    // 接口：获取钱包信息 (对应前端的 getWalletInfo)
    @GetMapping("/{plate}")
    public ResponseEntity<?> getWalletInfo(@PathVariable("plate") String plate) {
        try {
            return ResponseEntity.ok(walletService.getWalletInfo(plate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 接口：模拟充值 (对应前端的 rechargeWallet)
    @PostMapping("/recharge")
    public ResponseEntity<?> recharge(@RequestBody Map<String, Object> body) {
        try {
            String plate = (String) body.get("plate");
            BigDecimal amount = new BigDecimal(body.get("amount").toString());

            walletService.recharge(plate, amount);

            return ResponseEntity.ok(Map.of("ok", true, "message", "充值成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 接口：购买或续费 VIP (对应前端的 buyVip)
    @PostMapping("/buy-vip")
    public ResponseEntity<?> buyVip(@RequestBody Map<String, Object> body) {
        try {
            String plate = (String) body.get("plate");
            // 前端默认传的可能是 30 天，容错处理
            int days = Integer.parseInt(body.getOrDefault("days", "30").toString());

            walletService.buyVip(plate, days);

            return ResponseEntity.ok(Map.of("ok", true, "message", "VIP 开通成功"));
        } catch (Exception e) {
            // 返回带有 error 字段的 JSON，这样前端可以直接 catch 到错误提示并弹窗
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}