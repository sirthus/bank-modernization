package com.modernize.bankbatch;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST interface for triggering and monitoring the batch pipeline.
 *
 * This is the "force run" equivalent in Control-M — a way to trigger the
 * pipeline on demand without waiting for the scheduled window. Useful for
 * reruns, manual testing, and integration with other systems.
 *
 * Not loaded in the sandbox profile — there, BatchRunner handles execution.
 *
 * Endpoints:
 *
 *   POST /api/batch/run
 *     Triggers the pipeline immediately. Runs synchronously — the HTTP call
 *     will wait until the pipeline finishes (typically 1–2 minutes for the
 *     full 150k-record set). Returns 409 if already running.
 *
 *   GET /api/batch/status
 *     Returns "running" or "idle". Use this to poll from a script or browser.
 *
 * HOW TO TEST LOCALLY (with dev profile running):
 *   curl -X POST http://localhost:8080/api/batch/run
 *   curl       http://localhost:8080/api/batch/status
 */
@RestController
@RequestMapping("/api/batch")
@Profile("!sandbox")
public class BatchController {

    private final BatchPipelineService pipelineService;

    public BatchController(BatchPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/run")
    public ResponseEntity<String> run() {
        try {
            pipelineService.run();
            return ResponseEntity.ok("Pipeline completed successfully");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Pipeline failed: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok(pipelineService.isRunning() ? "running" : "idle");
    }
}
