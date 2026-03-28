package com.wenze.alpr.parking.service;

import com.wenze.alpr.parking.model.Vehicle;
import com.wenze.alpr.parking.model.Wallet;
import com.wenze.alpr.parking.model.WalletTransaction;
import com.wenze.alpr.parking.repo.VehicleRepo;
import com.wenze.alpr.parking.repo.WalletRepo;
import com.wenze.alpr.parking.repo.WalletTransactionRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class WalletService {
    private final WalletRepo walletRepo;
    private final WalletTransactionRepo transactionRepo;
    private final VehicleRepo vehicleRepo;

    public WalletService(WalletRepo walletRepo, WalletTransactionRepo transactionRepo, VehicleRepo vehicleRepo) {
        this.walletRepo = walletRepo;
        this.transactionRepo = transactionRepo;
        this.vehicleRepo = vehicleRepo;
    }

    // 1. 获取钱包和VIP信息
    public Map<String, Object> getWalletInfo(String plate) {
        // 如果找不到钱包，就自动为该车牌创建一个0元余额的新钱包
        Wallet wallet = walletRepo.findByPlate(plate).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setPlate(plate);
            w.setBalance(BigDecimal.ZERO);
            return walletRepo.save(w);
        });

        // 查询车辆的 VIP 到期时间
        Instant vipExpire = vehicleRepo.findByPlate(plate)
                .map(Vehicle::getVipExpireTime)
                .orElse(null);

        Map<String, Object> res = new HashMap<>();
        res.put("balance", wallet.getBalance());
        res.put("vipExpireTime", vipExpire);
        return res;
    }

    // 2. 充值逻辑 (加 @Transactional 保证事务)
    @Transactional
    public void recharge(String plate, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("充值金额必须大于0");
        }

        Wallet wallet = walletRepo.findByPlate(plate).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setPlate(plate);
            w.setBalance(BigDecimal.ZERO);
            return w;
        });

        // 增加余额
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepo.save(wallet);

        // 记录流水
        WalletTransaction tx = new WalletTransaction();
        tx.setPlate(plate);
        tx.setAmount(amount);
        tx.setType(WalletTransaction.Type.RECHARGE);
        tx.setDescription("充值 " + amount + " ₽");
        transactionRepo.save(tx);
    }

    // 3. 购买 VIP 逻辑 (加 @Transactional 保证事务)
    @Transactional
    public void buyVip(String plate, int days) {
        BigDecimal cost = new BigDecimal("5000.00");
        if (days != 30) {
            cost = new BigDecimal(days * 10); // 简单的动态计算规则：每天10卢布
        }

        Wallet wallet = walletRepo.findByPlate(plate)
                .orElseThrow(() -> new IllegalArgumentException("尚未开通钱包，请先充值"));

        // 检查余额
        if (wallet.getBalance().compareTo(cost) < 0) {
            throw new IllegalArgumentException("余额不足，开通需要 " + cost + " ₽，请先充值");
        }

        // 1. 扣费
        wallet.setBalance(wallet.getBalance().subtract(cost));
        walletRepo.save(wallet);

        // 2. 记录流水 (金额为负数)
        WalletTransaction tx = new WalletTransaction();
        tx.setPlate(plate);
        tx.setAmount(cost.negate());
        tx.setType(WalletTransaction.Type.BUY_VIP);
        tx.setDescription("购买 " + days + " 天 VIP");
        transactionRepo.save(tx);

        // 3. 更新车辆 VIP 时间
        Vehicle vehicle = vehicleRepo.findByPlate(plate).orElseGet(() -> {
            Vehicle v = new Vehicle();
            v.setPlate(plate);
            return v;
        });

        vehicle.setType(Vehicle.Type.whitelist); // 标记为白名单
        Instant now = Instant.now();
        Instant currentExpire = vehicle.getVipExpireTime();

        // 逻辑：如果之前已经是VIP且没过期，就在原有时间上叠加；如果是新开通或已过期，从今天开始算
        if (currentExpire != null && currentExpire.isAfter(now)) {
            vehicle.setVipExpireTime(currentExpire.plus(days, ChronoUnit.DAYS));
        } else {
            vehicle.setVipExpireTime(now.plus(days, ChronoUnit.DAYS));
        }
        vehicleRepo.save(vehicle);
    }
}