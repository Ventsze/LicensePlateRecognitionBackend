// src/main/java/com/wenze/alpr/config/StaticResourceConfig.java
package com.wenze.alpr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${alpr.output.dir}")
    private String outputDir;

    @Value("${alpr.thumbs.dir:#{null}}")
    private String thumbsDir; // 允许外部配置；未配置时用 outputDir/thumbs

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path filesPath = Paths.get(outputDir).toAbsolutePath().normalize();

        // /files/** → 磁盘视频目录
        registry.addResourceHandler("/files/**")
                .addResourceLocations(filesPath.toUri().toString())
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver());

        // /thumbs/** → 磁盘缩略图目录（优先用 alpr.thumbs.dir，否则默认 outputDir/thumbs）
        Path thumbsPath = (thumbsDir != null && !thumbsDir.isBlank())
                ? Paths.get(thumbsDir).toAbsolutePath().normalize()
                : filesPath.resolve("thumbs");
        registry.addResourceHandler("/thumbs/**")
                .addResourceLocations(thumbsPath.toUri().toString())
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver());

        // 可选：classpath 下的演示视频
        registry.addResourceHandler("/mp4/**")
                .addResourceLocations("classpath:/static/mp4/")
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver());
    }
}
