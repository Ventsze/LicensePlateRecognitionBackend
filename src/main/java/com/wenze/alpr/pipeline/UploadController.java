package com.wenze.alpr.pipeline;

import com.wenze.alpr.dto.AlprResponse;
import com.wenze.alpr.service.PythonPipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pipeline")
public class UploadController {

    private final PythonPipelineService pipeline;
    public UploadController(PythonPipelineService pipeline) { this.pipeline = pipeline; }

    @PostMapping("/process")
    public ResponseEntity<?> process(@RequestParam("file") MultipartFile file) {
        try {
            AlprResponse resp = pipeline.process(file);
            if (resp.error != null) {
                return ResponseEntity.internalServerError().body(resp);
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    java.util.Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage()));
        }
    }

    // 探针
    @GetMapping("/ping")
    public String ping() { return "pipeline-ok"; }
}
