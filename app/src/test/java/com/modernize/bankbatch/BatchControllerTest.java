package com.modernize.bankbatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchController.
 *
 * Pure unit test — no Spring context, no network. BatchController has one
 * dependency (BatchPipelineService) which is mocked here. We instantiate the
 * controller directly and call its methods to verify HTTP response codes and
 * body content.
 *
 * This tests the REST boundary: does the controller translate pipeline outcomes
 * (success, already-running, unexpected failure) into the right HTTP responses?
 */
class BatchControllerTest {

    private BatchPipelineService pipelineService;
    private BatchController controller;

    @BeforeEach
    void setUp() {
        pipelineService = mock(BatchPipelineService.class);
        controller = new BatchController(pipelineService);
    }

    // -------------------------------------------------------------------------
    // POST /api/batch/run
    // -------------------------------------------------------------------------

    /**
     * Happy path: pipeline runs to completion. Expect 200 OK.
     */
    @Test
    void run_successfulPipeline_returns200() throws Exception {
        // pipelineService.run() completes normally (no exception)
        ResponseEntity<String> response = controller.run();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("completed successfully");
    }

    /**
     * Conflict: pipeline is already running. Expect 409 CONFLICT.
     *
     * This is the guard that prevents two concurrent runs — the REST equivalent
     * of a Control-M job fence preventing double-submission.
     */
    @Test
    void run_pipelineAlreadyRunning_returns409() throws Exception {
        doThrow(new IllegalStateException("Pipeline is already running"))
            .when(pipelineService).run();

        ResponseEntity<String> response = controller.run();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already running");
    }

    /**
     * Unexpected failure: pipeline throws a generic exception. Expect 500.
     *
     * This covers scenarios like database unavailability, reconciliation failure,
     * or any other runtime exception that escapes the pipeline.
     */
    @Test
    void run_pipelineThrowsRuntimeException_returns500() throws Exception {
        doThrow(new RuntimeException("Pipeline halted: reconciliation failed"))
            .when(pipelineService).run();

        ResponseEntity<String> response = controller.run();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Pipeline failed");
    }

    // -------------------------------------------------------------------------
    // GET /api/batch/status
    // -------------------------------------------------------------------------

    /**
     * Status when pipeline is idle. Expect "idle" in response body.
     */
    @Test
    void status_whenIdle_returnsIdle() {
        when(pipelineService.isRunning()).thenReturn(false);

        ResponseEntity<String> response = controller.status();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("idle");
    }

    /**
     * Status when pipeline is active. Expect "running" in response body.
     *
     * This is what an operator or monitoring script would poll to know whether
     * it's safe to trigger another run.
     */
    @Test
    void status_whenRunning_returnsRunning() {
        when(pipelineService.isRunning()).thenReturn(true);

        ResponseEntity<String> response = controller.status();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("running");
    }
}
