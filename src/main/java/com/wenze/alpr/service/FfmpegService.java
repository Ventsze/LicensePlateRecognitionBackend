package com.wenze.alpr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;

/**
 * 生成并缓存视频缩略图。依赖本机 ffmpeg 可执行程序（macOS: brew install ffmpeg）。
 */
@Service
public class FfmpegService {
    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);

    private final Path thumbsDir;

    public FfmpegService(@Value("${alpr.thumbs.dir:./thumbs}") String thumbsDir) {
        this.thumbsDir = Paths.get(thumbsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.thumbsDir);
        } catch (IOException e) {
            // 不要让 Bean 创建失败，记录警告即可
            log.warn("Cannot create thumbs dir {}: {}", this.thumbsDir, e.toString());
        }
    }

    /**
     * 为视频生成/返回缩略图 URL。已存在且比视频新则复用。
     * @param videoFile 物理路径
     * @return 形如 "/thumbs/xxx.jpg" 的相对 URL；失败返回 null
     */
    public String getOrCreateThumb(Path videoFile) {
        try {
            if (videoFile == null || !Files.exists(videoFile)) return null;

            String base = stripExt(videoFile.getFileName().toString());
            Path out = thumbsDir.resolve(base + ".jpg");

            boolean needGen = !Files.exists(out);
            if (!needGen) {
                try {
                    needGen = Files.getLastModifiedTime(out).toMillis()
                            < Files.getLastModifiedTime(videoFile).toMillis();
                } catch (IOException ignore) {
                    needGen = true;
                }
            }

            if (needGen) {
                boolean ok = generateThumbResilient(
                        videoFile.toAbsolutePath().toString(),
                        out.toAbsolutePath().toString()
                );
                if (!ok) return null;
            }
            return "/thumbs/" + out.getFileName().toString();
        } catch (Exception e) {
            log.warn("Generate thumb failed for {}: {}", videoFile, e.toString());
            return null;
        }
    }

    /** 尝试两套命令：先快后稳；成功返回 true。 */
    private boolean generateThumbResilient(String input, String output) throws IOException, InterruptedException {
        // 尝试 1：-ss 在 -i 前（快，但对部分 webm/首秒无关键帧可能失败）
        List<String> cmd1 = List.of(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-ss", "00:00:01.000",
                "-i", input,
                "-frames:v", "1",
                "-vf", "scale=320:-1:flags=lanczos",
                "-q:v", "3",
                output
        );
        int c1 = run(cmd1);
        if (c1 == 0 && Files.exists(Paths.get(output))) return true;

        // 尝试 2：-ss 放在 -i 之后（慢，但更稳，适合首秒无关键帧/部分容器）
        List<String> cmd2 = List.of(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-i", input,
                "-ss", "00:00:00.100",
                "-frames:v", "1",
                "-vf", "scale=320:-1:flags=lanczos",
                "-q:v", "3",
                output
        );
        int c2 = run(cmd2);
        if (c2 == 0 && Files.exists(Paths.get(output))) return true;

        log.warn("Generate thumb failed for {}: ffmpeg exit {} then {}", input, c1, c2);
        return false;
    }

    /** 执行外部命令并耗尽输出，避免阻塞。返回退出码。 */
    private int run(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        try (InputStream is = new BufferedInputStream(p.getInputStream())) {
            // 把输出流读空，避免缓冲区满导致进程阻塞
            byte[] buf = new byte[8192];
            while (is.read(buf) != -1) { /* discard */ }
        }
        return p.waitFor();
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(0, i) : name;
    }
}
