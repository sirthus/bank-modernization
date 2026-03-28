package com.modernize.contractreplay.showcase;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioShowcaseDocumentationTest {

    @Test
    void checkedInSampleArtifacts_shouldStayInSyncWithTheGenerators() throws IOException {
        assertEquals(
            normalize(PortfolioShowcaseArtifacts.sampleMarkdown()),
            normalize(readFile(PortfolioShowcaseArtifacts.SAMPLE_MARKDOWN_PATH)));
        assertEquals(
            normalize(PortfolioShowcaseArtifacts.sampleJson()),
            normalize(readFile(PortfolioShowcaseArtifacts.SAMPLE_JSON_PATH)));
        assertEquals(
            normalize(PortfolioShowcaseArtifacts.guardrailMatrixDocument()),
            normalize(readFile(PortfolioShowcaseArtifacts.GUARDRAIL_MATRIX_PATH)));
    }

    @Test
    void readme_shouldExposeThePortfolioArtifactsAndEmbedTheGeneratedGuardrailMatrix() throws IOException {
        String readme = readFile("README.md");

        assertTrue(readme.contains("## What This Suite Proves"));
        assertTrue(readme.contains("## Why These Boundaries and Scenarios Were Chosen"));
        assertTrue(readme.contains("## How Contracts, Fixtures, and Reports Relate"));
        assertTrue(readme.contains("## Example Failure Modes This Suite Is Designed to Catch"));
        assertTrue(readme.contains("## QA Design Choices"));
        assertTrue(readme.contains("## How to Run It Locally"));
        assertTrue(readme.contains("docs/examples/portfolio-sample-suite-result.md"));
        assertTrue(readme.contains("docs/examples/portfolio-sample-suite-result.json"));
        assertTrue(readme.contains("docs/examples/guardrail-matrix.md"));

        String generatedBlock = extractGeneratedGuardrailBlock(readme);
        assertEquals(
            normalize(PortfolioShowcaseArtifacts.guardrailMatrixTable().trim()),
            normalize(generatedBlock.trim()));
    }

    private String readFile(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    private String extractGeneratedGuardrailBlock(String readme) {
        String startMarker = "<!-- BEGIN GENERATED GUARDRAIL MATRIX -->";
        String endMarker = "<!-- END GENERATED GUARDRAIL MATRIX -->";
        int start = readme.indexOf(startMarker);
        int end = readme.indexOf(endMarker);
        if (start < 0 || end < 0 || end < start) {
            throw new IllegalStateException("README guardrail matrix markers are missing or out of order");
        }
        return readme.substring(start + startMarker.length(), end).trim();
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").trim();
    }
}
