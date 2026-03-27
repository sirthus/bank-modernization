package com.modernize.verificationlab.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernize.verificationlab.model.BaselineOutput;

import java.io.InputStream;

/**
 * Loads a checked-in baseline.json file from the test classpath.
 *
 * Baselines live at: expected-output/{datasetId}/baseline.json
 *
 * A missing baseline is always a setup error, not a verification finding —
 * the lab cannot run without a baseline to compare against.
 */
public class BaselineLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BaselineLoader() {}

    public static BaselineOutput load(String datasetId) {
        String path = "expected-output/" + datasetId + "/baseline.json";
        InputStream stream = BaselineLoader.class.getClassLoader().getResourceAsStream(path);

        if (stream == null) {
            throw new IllegalStateException(
                "Baseline not found on classpath: " + path
                + " — check that it exists in src/test/resources/expected-output/" + datasetId + "/"
            );
        }

        try {
            return MAPPER.readValue(stream, BaselineOutput.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse baseline at " + path + ": " + e.getMessage(), e);
        }
    }
}
