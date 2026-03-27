package com.modernize.verificationlab.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.modernize.verificationlab.model.VerificationResult;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Serializes a VerificationResult to a pretty-printed JSON file.
 *
 * The JSON report is the machine-readable artifact — suitable for CI artifact
 * storage, downstream tooling, and diffing between runs.
 */
public class JsonReportGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonReportGenerator() {}

    public static void write(VerificationResult result, Path outputPath) throws IOException {
        MAPPER.writeValue(outputPath.toFile(), result);
    }
}
