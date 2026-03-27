// src/main/java/com/wenze/alpr/controller/RunsController.java
package com.wenze.alpr.controller;

import com.wenze.alpr.service.FfmpegService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

@RestController
@RequestMapping("/api/alpr")
public class RunsController {

    private final Path filesDir;
    private final FfmpegService ffmpeg;

    public RunsController(@Value("${alpr.output.dir:./files}") String filesDir,
                          FfmpegService ffmpeg) throws IOException {
        this.filesDir = Paths.get(filesDir).toAbsolutePath().normalize();
        this.ffmpeg = ffmpeg;
        Files.createDirectories(this.filesDir); // 确保目录存在
    }

    @GetMapping("/runs")
    public Map<String, Object> runs() throws IOException {
        if (!Files.isDirectory(filesDir)) {
            return Map.of("runs", List.of());
        }

        try (Stream<Path> s = Files.list(filesDir)) {
            List<Map<String, Object>> arr = s
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        // 兼容多种视频后缀
                        return n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mov") || n.endsWith(".m4v");
                    })
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .limit(50)
                    .map(p -> {
                        String fileName = p.getFileName().toString();
                        String run = fileName.replaceFirst("\\.[^.]+$", "");
                        String downloadUrl = "/files/" + fileName;

                        // 生成或获取缩略图（允许失败，失败则不返回该字段）
                        String thumbUrl = null;
                        try {
                            // 如果你的 FfmpegService 返回的是 Path，请用：
                            // Path thumb = ffmpeg.getOrCreateThumb(p);
                            // thumbUrl = (thumb != null) ? "/thumbs/" + thumb.getFileName().toString() : null;

                            // 当前你的实现看起来返回的是 String（可能为 null）
                            thumbUrl = ffmpeg.getOrCreateThumb(p);
                        } catch (Exception ignore) {
                            // 生成失败就不带 thumb_url，避免 500
                        }

                        long size = p.toFile().length();
                        long modified = p.toFile().lastModified();

                        // 用可变 Map，避免 Map.of 的 null 限制
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("run", run);
                        m.put("download_url", downloadUrl);
                        m.put("size", size);
                        m.put("modified", modified);
                        if (thumbUrl != null && !thumbUrl.isBlank()) {
                            m.put("thumb_url", thumbUrl);
                        }
                        return m;
                    })
                    .collect(Collectors.toList());

            return Map.of("runs", arr);
        }
    }
}
