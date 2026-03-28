package com.wenze.alpr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults()) // 允许跨域
                .csrf(csrf -> csrf.disable())    // 关闭 CSRF

                // 1. 配置权限拦截规则
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/api/login", "/index.html", "/static/**", "/assets/**", "/api/parking/**","/api/wallet/**").permitAll()
                        .anyRequest().authenticated()
                )

                // 2. 配置表单登录 (完全前后端分离的 JSON 响应模式)
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/api/login") // 接收前端 POST 请求的路径
                        // 登录成功返回 JSON
                        .successHandler((request, response, authentication) -> {
                            response.setContentType("application/json;charset=utf-8");
                            response.getWriter().write("{\"status\":\"success\", \"msg\":\"登录成功\"}");
                        })
                        // 登录失败返回 JSON 与 401 状态码
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=utf-8");
                            response.getWriter().write("{\"status\":\"error\", \"msg\":\"账号或密码错误\"}");
                        })
                        .permitAll()
                )

                // 3. 配置注销登录 (同样返回 JSON)
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setContentType("application/json;charset=utf-8");
                            response.getWriter().write("{\"status\":\"success\", \"msg\":\"已注销\"}");
                        })
                        .permitAll()
                );

        return http.build();
    }

    // 4. 使用工业标准的 BCrypt 加密
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 5. 跨域配置
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
                "http://localhost:*", "https://localhost:*",
                "http://127.0.0.1:*", "https://127.0.0.1:*",
                "http://192.168.31.103:*", "https://192.168.31.103:*",
                "http://198.18.0.1:*", "https://198.18.0.1:*",
                "http://zhangwenze.local:*", "https://zhangwenze.local:*"
        ));
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true); // 必须为 true，允许携带 Session
        cfg.setExposedHeaders(List.of("Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}