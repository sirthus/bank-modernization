package com.modernize.contractreplay.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractStatus;
import com.modernize.contractreplay.model.ContractViolation;

import java.io.InputStream;

/**
 * Verifies an actual HTTP response against an API contract definition file.
 *
 * Reads a contract JSON from the classpath (e.g. contracts/api/batch-run-contract.json)
 * and compares a caller-supplied actual response (status code + content type + body)
 * against the named scenario's expectations.
 *
 * Checks performed:
 *   1. HTTP status code — must match expectedStatus exactly (FAIL)
 *   2. Content-Type — compared against the contract media type when present (FAIL)
 *   3. Response body — matched against whichever of these is present in the contract:
 *      - expectedBodyExact    → full string equality (FAIL)
 *      - expectedBodyContains → substring match (FAIL)
 *      - expectedBodyStartsWith → prefix match (FAIL)
 *   4. Plain-text content — body must not be parseable as a JSON object/array (FAIL)
 *      Catches accidental JSON-wrapping of plain-text responses.
 *
 * Usage:
 *   ApiContractVerifier verifier = new ApiContractVerifier("contracts/api/batch-run-contract.json");
 *   ContractResult result = verifier.verify("clean_trigger", actualStatus, actualContentType, actualBody);
 *
 * The returned ContractResult is used by ApiContractTest both for assertions and
 * for inclusion in the suite-level JSON/Markdown report.
 */
public class ApiContractVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String contractPath;

    public ApiContractVerifier(String contractPath) {
        this.contractPath = contractPath;
    }

    /**
     * Verifies actualStatus, actualContentType, and actualBody against the named scenario in the contract file.
     *
     * @param scenarioId  the "scenario" field value in the contract JSON responses array
     * @param actualStatus  the HTTP status code received
     * @param actualContentType  the response Content-Type header value received
     * @param actualBody    the response body string received (trimmed by caller)
     * @return ContractResult — PASS if all checks pass, FAIL otherwise
     */
    public ContractResult verify(String scenarioId, int actualStatus, String actualContentType, String actualBody) {
        JsonNode contract;
        String contractId;

        try {
            InputStream is = ApiContractVerifier.class
                    .getClassLoader()
                    .getResourceAsStream(contractPath);
            if (is == null) {
                ContractResult missing = new ContractResult("API-CONTRACT-SETUP", scenarioId);
                missing.addViolation(new ContractViolation(
                    "API-SETUP-NOT-FOUND",
                    "Contract definition file not found on classpath: " + contractPath,
                    ContractStatus.FAIL, contractPath, "not found"));
                return missing;
            }
            contract = MAPPER.readTree(is);
        } catch (Exception e) {
            ContractResult error = new ContractResult("API-CONTRACT-SETUP", scenarioId);
            error.addViolation(new ContractViolation(
                "API-SETUP-PARSE-ERROR",
                "Failed to parse contract definition: " + e.getMessage(),
                ContractStatus.FAIL, "valid JSON", e.getMessage()));
            return error;
        }

        contractId = contract.path("contractId").asText("API-UNKNOWN");
        ContractResult result = new ContractResult(contractId, scenarioId);

        // Find the matching scenario node
        JsonNode scenarioNode = null;
        JsonNode responses = contract.path("responses");
        for (JsonNode response : responses) {
            if (scenarioId.equals(response.path("scenario").asText())) {
                scenarioNode = response;
                break;
            }
        }

        if (scenarioNode == null) {
            result.addViolation(new ContractViolation(
                contractId + "-SCENARIO-NOT-FOUND",
                "Scenario '" + scenarioId + "' not found in contract " + contractPath,
                ContractStatus.FAIL, "scenario present", "scenario absent"));
            return result;
        }

        // ── Check 1: HTTP status code ─────────────────────────────────────────
        int expectedStatus = scenarioNode.path("expectedStatus").asInt(-1);
        if (actualStatus != expectedStatus) {
            result.addViolation(new ContractViolation(
                contractId + "-STATUS",
                "HTTP status mismatch for scenario '" + scenarioId + "'",
                ContractStatus.FAIL,
                "status: " + expectedStatus,
                "status: " + actualStatus));
        }

        // ── Check 2: Content-Type ─────────────────────────────────────────────
        String expectedContentType = scenarioNode.path("contentType").asText("");
        if (!expectedContentType.isBlank()) {
            String normalizedExpected = normalizeMediaType(expectedContentType);
            String normalizedActual = normalizeMediaType(actualContentType);
            if (!normalizedExpected.equals(normalizedActual)) {
                result.addViolation(new ContractViolation(
                    contractId + "-CONTENT-TYPE",
                    "Content-Type mismatch for scenario '" + scenarioId + "'",
                    ContractStatus.FAIL,
                    "content-type: " + normalizedExpected,
                    "content-type: " + normalizedActual));
            }
        }

        // ── Check 3: Response body ────────────────────────────────────────────
        String body = actualBody == null ? "" : actualBody;

        if (scenarioNode.has("expectedBodyExact")) {
            String expected = scenarioNode.get("expectedBodyExact").asText();
            if (!body.equals(expected)) {
                result.addViolation(new ContractViolation(
                    contractId + "-BODY-EXACT",
                    "Response body does not match expected exact value for scenario '" + scenarioId + "'",
                    ContractStatus.FAIL,
                    "body: \"" + expected + "\"",
                    "body: \"" + body + "\""));
            }
        }

        if (scenarioNode.has("expectedBodyContains")) {
            String substring = scenarioNode.get("expectedBodyContains").asText();
            if (!body.contains(substring)) {
                result.addViolation(new ContractViolation(
                    contractId + "-BODY-CONTAINS",
                    "Response body does not contain expected substring for scenario '" + scenarioId + "'",
                    ContractStatus.FAIL,
                    "body contains: \"" + substring + "\"",
                    "body: \"" + body + "\""));
            }
        }

        if (scenarioNode.has("expectedBodyStartsWith")) {
            String prefix = scenarioNode.get("expectedBodyStartsWith").asText();
            if (!body.startsWith(prefix)) {
                result.addViolation(new ContractViolation(
                    contractId + "-BODY-PREFIX",
                    "Response body does not start with expected prefix for scenario '" + scenarioId + "'",
                    ContractStatus.FAIL,
                    "body starts with: \"" + prefix + "\"",
                    "body: \"" + body + "\""));
            }
        }

        // ── Check 4: Plain-text guard (no JSON wrapping) ──────────────────────
        // BatchController returns raw strings. If a refactor accidentally wraps
        // the response in a JSON object or array, this catches it.
        // Skip this guard when the contract explicitly expects a JSON content type —
        // a JSON body is correct in that case and should not be flagged.
        String contractMediaType = normalizeMediaType(expectedContentType);
        if (!contractMediaType.contains("json") && (body.startsWith("{") || body.startsWith("["))) {
            try {
                MAPPER.readTree(body);
                // If we get here, the body is valid JSON — that's a contract violation
                result.addViolation(new ContractViolation(
                    contractId + "-PLAINTEXT",
                    "Response body is valid JSON but contract requires plain text for scenario '" + scenarioId + "'",
                    ContractStatus.FAIL,
                    "content-type: text/plain, non-JSON body",
                    "body parses as JSON: " + body));
            } catch (Exception ignored) {
                // Body starts with { or [ but is not valid JSON — treat as plain text, no violation
            }
        }

        return result;
    }

    private String normalizeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        String mediaType = contentType.split(";", 2)[0].trim();
        return mediaType.toLowerCase();
    }
}
