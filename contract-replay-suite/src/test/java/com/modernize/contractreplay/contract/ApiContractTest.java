package com.modernize.contractreplay.contract;

import com.modernize.bankbatch.BankBatchApplication;
import com.modernize.bankbatch.BatchPipelineService;
import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the BatchController REST API matches the contracts defined in
 * contracts/api/batch-run-contract.json and contracts/api/batch-status-contract.json.
 *
 * Uses RANDOM_PORT so a real Tomcat servlet container starts and the full
 * request-dispatch path is exercised (filters, error handling, content negotiation).
 *
 * BatchPipelineService is mocked so each test can control the pipeline's
 * apparent state without running any Spring Batch jobs. The contract under test
 * is the HTTP layer — not the pipeline internals (those are covered by the
 * RP-00* replay tests).
 *
 * Tests:
 *   1. GET /api/batch/status when idle → 200, "idle"
 *   2. GET /api/batch/status when running → 200, "running"
 *   3. POST /api/batch/run — clean trigger → 200, "Pipeline completed successfully"
 *   4. POST /api/batch/run — concurrent trigger → 409, body contains "already running"
 *   5. POST /api/batch/run — pipeline failure → 500, body starts with "Pipeline failed:"
 *   6. POST /api/batch/run response body is not a JSON object or array
 *   7. GET /api/batch/status response body is not a JSON object or array
 */
@SpringBootTest(
    classes = BankBatchApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("contracttest")
class ApiContractTest {

    private static final String RUN_CONTRACT    = "contracts/api/batch-run-contract.json";
    private static final String STATUS_CONTRACT = "contracts/api/batch-status-contract.json";

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private BatchPipelineService pipelineService;

    @BeforeEach
    void resetMock() throws Exception {
        reset(pipelineService);
        when(pipelineService.isRunning()).thenReturn(false);
        // run() returns void; default mock behaviour is do-nothing (completes normally)
    }

    // ── Status endpoint ───────────────────────────────────────────────────────

    @Test
    void idleStatus_shouldReturn200WithBodyIdle() {
        when(pipelineService.isRunning()).thenReturn(false);

        ResponseEntity<String> response = restTemplate.getForEntity("/api/batch/status", String.class);
        ContractResult result = new ApiContractVerifier(STATUS_CONTRACT)
                .verify("pipeline_idle", response.getStatusCode().value(), response.getHeaders().getFirst("Content-Type"), response.getBody());

        assertEquals(ContractStatus.PASS, result.getOverallStatus(),
            "Contract violations: " + result.getViolations());
    }

    @Test
    void runningStatus_shouldReturn200WithBodyRunning() {
        when(pipelineService.isRunning()).thenReturn(true);

        ResponseEntity<String> response = restTemplate.getForEntity("/api/batch/status", String.class);
        ContractResult result = new ApiContractVerifier(STATUS_CONTRACT)
                .verify("pipeline_running", response.getStatusCode().value(), response.getHeaders().getFirst("Content-Type"), response.getBody());

        assertEquals(ContractStatus.PASS, result.getOverallStatus(),
            "Contract violations: " + result.getViolations());
    }

    // ── Run endpoint ─────────────────────────────────────────────────────────

    @Test
    void cleanTrigger_shouldReturn200WithSuccessBody() throws Exception {
        // Mock run() does nothing (no exception) — pipeline appears to complete
        doNothing().when(pipelineService).run();

        ResponseEntity<String> response = restTemplate.postForEntity("/api/batch/run", null, String.class);
        ContractResult result = new ApiContractVerifier(RUN_CONTRACT)
                .verify("clean_trigger", response.getStatusCode().value(), response.getHeaders().getFirst("Content-Type"), response.getBody());

        assertEquals(ContractStatus.PASS, result.getOverallStatus(),
            "Contract violations: " + result.getViolations());
    }

    @Test
    void concurrentTrigger_shouldReturn409() throws Exception {
        doThrow(new IllegalStateException("Pipeline is already running"))
                .when(pipelineService).run();

        ResponseEntity<String> response = restTemplate.postForEntity("/api/batch/run", null, String.class);
        ContractResult result = new ApiContractVerifier(RUN_CONTRACT)
                .verify("concurrent_trigger", response.getStatusCode().value(), response.getHeaders().getFirst("Content-Type"), response.getBody());

        assertEquals(ContractStatus.PASS, result.getOverallStatus(),
            "Contract violations: " + result.getViolations());
    }

    @Test
    void pipelineFailure_shouldReturn500WithFailedBody() throws Exception {
        doThrow(new RuntimeException("forced test failure — db connection lost"))
                .when(pipelineService).run();

        ResponseEntity<String> response = restTemplate.postForEntity("/api/batch/run", null, String.class);
        ContractResult result = new ApiContractVerifier(RUN_CONTRACT)
                .verify("pipeline_failure", response.getStatusCode().value(), response.getHeaders().getFirst("Content-Type"), response.getBody());

        assertEquals(ContractStatus.PASS, result.getOverallStatus(),
            "Contract violations: " + result.getViolations());
    }

    // ── Plain-text guard ──────────────────────────────────────────────────────

    @Test
    void runResponse_bodyIsNotJson() throws Exception {
        doNothing().when(pipelineService).run();

        ResponseEntity<String> response = restTemplate.postForEntity("/api/batch/run", null, String.class);
        String body = response.getBody();

        assertNotNull(body, "Response body should not be null");
        assertFalse(body.trim().startsWith("{"),
            "Run endpoint must return plain text, not a JSON object. Got: " + body);
        assertFalse(body.trim().startsWith("["),
            "Run endpoint must return plain text, not a JSON array. Got: " + body);
    }

    @Test
    void statusResponse_bodyIsNotJson() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/batch/status", String.class);
        String body = response.getBody();

        assertNotNull(body, "Response body should not be null");
        assertFalse(body.trim().startsWith("{"),
            "Status endpoint must return plain text, not a JSON object. Got: " + body);
        assertFalse(body.trim().startsWith("["),
            "Status endpoint must return plain text, not a JSON array. Got: " + body);
    }
}
