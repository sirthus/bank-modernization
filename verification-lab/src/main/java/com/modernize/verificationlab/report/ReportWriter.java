package com.modernize.verificationlab.report;

import com.modernize.verificationlab.model.VerificationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Coordinates JSON and HTML report generation for one verification run.
 *
 * Reports are written to the verification-lab/reports/ directory, which is
 * git-ignored. In CI, the reports directory is published as a workflow artifact
 * keyed to the commit SHA so every run has a permanent evidence trail.
 *
 * File naming: verification-{datasetId}-{yyyyMMdd_HHmmss}.json / .html
 */
public class ReportWriter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ReportWriter() {}

    public static void write(VerificationResult result) throws IOException {
        Path reportsDir = resolveReportsDir();
        Files.createDirectories(reportsDir);

        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        String baseName  = "verification-" + result.getDatasetId() + "-" + timestamp;

        Path jsonPath = reportsDir.resolve(baseName + ".json");
        Path htmlPath = reportsDir.resolve(baseName + ".html");

        JsonReportGenerator.write(result, jsonPath);
        HtmlReportGenerator.write(result, htmlPath);

        System.out.println("[VerificationLab] Reports written:");
        System.out.println("  JSON: " + jsonPath.toAbsolutePath());
        System.out.println("  HTML: " + htmlPath.toAbsolutePath());
    }

    /**
     * Resolves the reports directory relative to the verification-lab module root.
     *
     * Maven sets user.dir to the module directory when running tests, so
     * "reports/" resolves correctly whether running from the module or the reactor.
     */
    private static Path resolveReportsDir() {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        // If running from the reactor root, navigate into the module
        if (!workingDir.endsWith("verification-lab")) {
            workingDir = workingDir.resolve("verification-lab");
        }
        return workingDir.resolve("reports");
    }
}
