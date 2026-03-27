package com.wenze.alpr.service;

import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import com.wenze.alpr.dto.StatItem;
import com.wenze.alpr.dto.AlprResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

@Service
public class PythonPipelineService {

    @Value("${alpr.python.bin}")   private String configuredPy;   // venv 的 python 或 /usr/bin/env
    @Value("${alpr.project.root}") private String projectRoot;    // 项目根（含 main.py）
    @Value("${alpr.assets.dir}")   private String assetsDir;      // assets 目录（含 add_missing_data.py / visualize.py）
    @Value("${alpr.output.dir}")   private String outputDir;      // 最终可下载文件目录

    // ========= 入口 =========
    public AlprResponse process(MultipartFile file) {
        AlprResponse resp = new AlprResponse();
        try {
            Path root    = Paths.get(projectRoot);
            Path assets  = Paths.get(assetsDir);
            Path outputs = Paths.get(outputDir);

            Files.createDirectories(assets);
            Files.createDirectories(outputs);

            // 选用并探针验证 Python 解释器
            String python = pickUsablePython();

            // 校验脚本位置
            Path mainPy      = root.resolve("main.py");                   ensureExists(mainPy);
            Path addMissing  = assets.resolve("add_missing_data.py");     ensureExists(addMissing);
            Path visualize   = assets.resolve("visualize.py");            ensureExists(visualize);

            // 1) 保存上传为 assets/sample.mp4（visualize.py 读取该名称）
            Path uploaded = assets.resolve("sample.mp4");
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(uploaded, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                FileCopyUtils.copy(in, out);
            }

            // 2) 根目录执行 main.py 生成 ./test.csv
            run(python, mainPy.toString(), root);

            // 3) assets 下执行插帧，读取 ../test.csv，输出 assets/test_interpolated.csv
            run(python, addMissing.toString(), assets);

            // 4) assets 下可视化，读取 test_interpolated.csv 与 sample.mp4，输出 assets/out.mp4
            run(python, visualize.toString(), assets);

            // 4.5) 尝试转码为 H.264（浏览器友好），失败则回退原 out.mp4
            Path rawOut   = assets.resolve("out.mp4");
            Path playable = transcodeToH264IfPossible(rawOut, assets);

            // 5) 规范命名并复制到下载目录
            String finalName = buildFinalName(file.getOriginalFilename());
            Path finalOut = outputs.resolve(finalName);
            Files.copy(playable, finalOut, StandardCopyOption.REPLACE_EXISTING);

            resp.download_url = "/files/" + finalName;
            resp.message = "OK";

            // === 统计：优先使用 assets/test_interpolated.csv，其次项目根 test.csv ===
            Path csvInterpolated = Paths.get(assetsDir).resolve("test_interpolated.csv");
            Path csvRaw          = Paths.get(projectRoot).resolve("test.csv");
            List<StatItem> stats;
            if (Files.exists(csvInterpolated)) {
                stats = computeStatsFromCsv(csvInterpolated);
            } else {
                stats = computeStatsFromCsv(csvRaw);
            }
            resp.stats = stats;

            return resp;

        } catch (Exception e) {
            resp.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            return resp;
        }
    }

    // ========= 工具方法 =========

    private void ensureExists(Path p) {
        if (!Files.exists(p)) {
            throw new RuntimeException("missing " + p);
        }
    }

    /** 只使用配置的解释器，失败直接报错（不回退系统 python，避免“看似成功实际跑错环境”） */
    private String pickUsablePython() throws Exception {
        if (configuredPy == null || configuredPy.isBlank()) {
            throw new RuntimeException("alpr.python.bin is empty");
        }
        String path = configuredPy.trim();
        Path p = Paths.get(path);
        if (!Files.exists(p))               throw new RuntimeException("Python not found: " + p);
        if (!Files.isRegularFile(p))        throw new RuntimeException("Python is not a regular file: " + p);
        if (!Files.isExecutable(p))         throw new RuntimeException("Python is not executable: " + p);

        // 探针打印实际解释器（确认没有被 wrapper 改写）
        ProcessBuilder pb = new ProcessBuilder(path, "-c", "import sys; print('PY', sys.executable)");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = proc.waitFor();
        System.out.println("[ALPR] python probe output:\n" + out);
        if (code != 0) {
            throw new RuntimeException("Configured python failed to run: exit=" + code + "\n" + out);
        }
        return path;
    }

    /** 运行单个 Python 脚本 */
    private void run(String python, String scriptPath, Path workdir) throws Exception {
        List<String> cmd = python.startsWith("/usr/bin/env ")
                ? Arrays.asList("/usr/bin/env", "python3", scriptPath)
                : Arrays.asList(python, scriptPath);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workdir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        String out;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            out = sb.toString();
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("Fail: " + String.join(" ", cmd) + "\n---OUTPUT---\n" + out);
        }
    }

    /** 尝试用 ffmpeg 转成 h264+yuv420p+faststart，成功返回新文件；失败返回原文件 */
    private Path transcodeToH264IfPossible(Path rawOut, Path assetsDirPath) {
        Path webOut = assetsDirPath.resolve("out_web.mp4");
        try {
            ProcessBuilder ff = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", rawOut.toString(),
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart",
                    "-profile:v", "baseline",
                    "-level", "3.0",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    webOut.toString()
            );
            ff.redirectErrorStream(true);
            ff.directory(assetsDirPath.toFile());
            Process p = ff.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                while (br.readLine() != null) { /* eat ffmpeg output */ }
            }
            if (p.waitFor() == 0 && Files.exists(webOut)) {
                return webOut;
            }
        } catch (Exception ignore) {
            // 未安装 ffmpeg 或执行失败 → 回退原文件
        }
        return rawOut;
    }

    /** 生成更干净的最终文件名：把时间戳插到扩展名前 */
    private String buildFinalName(String original) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safe = sanitizeFilename(original == null ? "video.mp4" : original);
        int dot = safe.lastIndexOf('.');
        String base = (dot > 0 ? safe.substring(0, dot) : safe);
        return base + "_" + stamp + ".mp4";
    }

    /** 仅保留常见安全字符，避免路径奇怪字符导致的问题 */
    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ========= 读取 CSV 并统计：每个车牌的 max_text_score 与 samples =========
    private List<StatItem> computeStatsFromCsv(Path csv) throws IOException {
        if (!Files.exists(csv)) return List.of();

        // 尝试用 UTF-8 读取
        try (BufferedReader br = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) return List.of();

            // 解析表头，找列索引（列名不区分大小写）
            String[] heads = header.split(",", -1);
            int idxPlate = -1, idxText = -1;
            for (int i = 0; i < heads.length; i++) {
                String h = heads[i].trim().toLowerCase();
                if (h.equals("license_number")) idxPlate = i;
                if (h.equals("license_number_score")) idxText = i;
            }
            if (idxPlate < 0) return List.of(); // 没有车牌列
            // 如果没有文本分数列，也允许—只统计样本数
            boolean hasText = idxText >= 0;

            // 统计
            Map<String, StatItem> agg = new java.util.HashMap<>();
            String line;
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length <= idxPlate) continue;
                String plate = cols[idxPlate].trim();
                if (plate.isEmpty()) continue;

                Double textScore = null;
                if (hasText && cols.length > idxText && !cols[idxText].isBlank()) {
                    try { textScore = Double.valueOf(cols[idxText]); } catch (NumberFormatException ignore) {}
                }

                StatItem cur = agg.get(plate);
                if (cur == null) {
                    agg.put(plate, new StatItem(plate, textScore, 1));
                } else {
                    cur.samples = (cur.samples == null ? 0 : cur.samples) + 1;
                    if (textScore != null) {
                        if (cur.max_text_score == null || textScore > cur.max_text_score) {
                            cur.max_text_score = textScore;
                        }
                    }
                }
            }

            // 排序：先按 max_text_score（空的排后），再按 samples
            return agg.values().stream()
                    .sorted((a,b) -> {
                        int c = Double.compare(b.max_text_score == null ? -1 : b.max_text_score,
                                a.max_text_score == null ? -1 : a.max_text_score);
                        if (c != 0) return c;
                        return Integer.compare(b.samples == null ? 0 : b.samples,
                                a.samples == null ? 0 : a.samples);
                    })
                    .collect(Collectors.toList());
        }
    }

    public List<StatItem> computeLatestStats() throws Exception {
        Path csvInterpolated = Paths.get(assetsDir).resolve("test_interpolated.csv");
        Path csvRaw          = Paths.get(projectRoot).resolve("test.csv");
        if (Files.exists(csvInterpolated)) return computeStatsFromCsv(csvInterpolated);
        return computeStatsFromCsv(csvRaw);
    }

}
