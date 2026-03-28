package com.modernize.contractreplay.contract;

import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiContractVerifierTest {

    private static final String RUN_CONTRACT = "contracts/api/batch-run-contract.json";

    @Test
    void contentTypeMismatch_shouldFailEvenWhenStatusAndBodyMatch() {
        ContractResult result = new ApiContractVerifier(RUN_CONTRACT)
            .verify("clean_trigger", 200, "application/json;charset=UTF-8", "Pipeline completed successfully");

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.getRuleId().equals("API-BATCH-RUN-001-CONTENT-TYPE")),
            "Expected content-type violation but got: " + result.getViolations());
    }

    @Test
    void contentTypeWithCharset_shouldPassMediaTypeComparison() {
        ContractResult result = new ApiContractVerifier(RUN_CONTRACT)
            .verify("clean_trigger", 200, "Text/Plain; charset=UTF-8", "Pipeline completed successfully");

        assertEquals(ContractStatus.PASS, result.getOverallStatus(),
            "Expected text/plain with charset to satisfy the contract: " + result.getViolations());
    }

    @Test
    void validJsonBody_whenContractExpectsPlainText_shouldFailWithPlaintextViolation() {
        // The clean_trigger scenario has contentType: text/plain.
        // Passing a valid JSON body should trigger the -PLAINTEXT guard.
        ContractResult result = new ApiContractVerifier(RUN_CONTRACT)
            .verify("clean_trigger", 200, "text/plain", "{\"status\":\"ok\"}");

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(result.getViolations().stream()
                .anyMatch(v -> v.getRuleId().endsWith("-PLAINTEXT")),
            "Expected a -PLAINTEXT violation but got: " + result.getViolations());
    }
}
