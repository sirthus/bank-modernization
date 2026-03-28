package com.modernize.contractreplay.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

public final class ReplayExpectationLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReplayExpectationLoader() {}

    public static ReplayExpectation load(String classpathResource) {
        try (InputStream is = ReplayExpectationLoader.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (is == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + classpathResource);
            }
            ReplayExpectation expectation = MAPPER.readValue(is, ReplayExpectation.class);
            validate(expectation, classpathResource);
            return expectation;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load replay expectation: " + classpathResource, e);
        }
    }

    private static void validate(ReplayExpectation expectation, String classpathResource) {
        require(expectation.getScenarioId(), "scenarioId", classpathResource);
        require(expectation.getDescription(), "description", classpathResource);
        require(expectation.getPurpose(), "purpose", classpathResource);
        require(expectation.getFixtureFile(), "fixtureFile", classpathResource);
        require(expectation.getRunsSubmitted(), "runsSubmitted", classpathResource);
        require(expectation.getExpectedStagedCount(), "expectedStagedCount", classpathResource);
        require(expectation.getExpectedPostedCount(), "expectedPostedCount", classpathResource);
        require(expectation.getExpectedRejectedCount(), "expectedRejectedCount", classpathResource);
        require(expectation.getExpectedErrorCount(), "expectedErrorCount", classpathResource);
        require(expectation.getExpectedTotalPostedCents(), "expectedTotalPostedCents", classpathResource);
        require(expectation.getWhatWouldBreakThis(), "whatWouldBreakThis", classpathResource);

        if (expectation.getRunsSubmitted() != null && expectation.getRunsSubmitted() < 1) {
            throw new IllegalStateException(
                "Replay expectation field 'runsSubmitted' must be > 0 in " + classpathResource
                + " (was: " + expectation.getRunsSubmitted() + ")");
        }

        if (expectation.getExpectedReconciliation() == null
                && expectation.getExpectedPerRun() == null
                && expectation.getExpectedAfterBothRuns() == null) {
            throw new IllegalStateException("Replay expectation must define reconciliation expectations: " + classpathResource);
        }
    }

    private static void require(Object value, String fieldName, String classpathResource) {
        if (value == null || (value instanceof String string && string.isBlank())) {
            throw new IllegalStateException("Missing required replay expectation field '" + fieldName + "' in " + classpathResource);
        }
    }
}
