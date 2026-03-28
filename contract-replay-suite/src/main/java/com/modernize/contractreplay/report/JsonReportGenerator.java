package com.modernize.contractreplay.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.modernize.contractreplay.model.SuiteResult;

/**
 * Serializes a SuiteResult to a pretty-printed JSON string.
 *
 * The JSON output is the machine-readable evidence artifact for the suite run.
 * It includes all contract results (with per-violation rule IDs) and all replay
 * scenario results (with counts and reconciliation flags).
 *
 * LocalDateTime fields are serialized via their toString() getter, which produces
 * valid ISO-8601 strings (e.g. "2025-03-27T14:23:01.123") without requiring
 * jackson-datatype-jsr310.
 */
public class JsonReportGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public String generate(SuiteResult suite) {
        try {
            return MAPPER.writeValueAsString(suite);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize suite result to JSON", e);
        }
    }
}
