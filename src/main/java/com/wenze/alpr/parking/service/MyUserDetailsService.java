package com.wenze.alpr.parking.service;

import com.wenze.alpr.parking.model.User;
import com.wenze.alpr.parking.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 通过你写的 UserRepo 去数据库查这个账号
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("找不到该用户: " + username));

        // 2. 把我们数据库的 User 包装成 Spring Security 认识的 UserDetails
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword()) // 这里的密码必须是加密后的
                .roles("USER") // 默认给个 USER 角色
                .build();
    }
}