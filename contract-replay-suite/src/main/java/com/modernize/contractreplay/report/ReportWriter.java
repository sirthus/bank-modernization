package com.modernize.contractreplay.report;

import com.modernize.contractreplay.model.SuiteResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes the JSON and Markdown suite reports to the reports/ directory.
 *
 * Filenames are timestamped to the second so that successive runs in the same
 * CI job do not overwrite each other:
 *   reports/suite-result-20250327-142301.json
 *   reports/suite-result-20250327-142301.md
 *
 * The reports/ directory is .gitignore'd — CI uploads the artifacts via
 * actions/upload-artifact so they are accessible from the build summary
 * without polluting the repository.
 *
 * If the reports/ directory does not exist, it is created. Any I/O failure
 * throws an unchecked RuntimeException so test failures are visible in the
 * CI log rather than silently swallowed.
 */
public class ReportWriter {

    private static final Path REPORTS_DIR = Paths.get("reports");

    private final JsonReportGenerator jsonGenerator;
    private final MarkdownReportGenerator markdownGenerator;

    public ReportWriter() {
        this.jsonGenerator = new JsonReportGenerator();
        this.markdownGenerator = new MarkdownReportGenerator();
    }

    /**
     * Generates and writes both report formats.
     *
     * @param suite the completed SuiteResult
     * @return the base path (without extension) used for both files, for test assertions
     */
    public Path write(SuiteResult suite) {
        // timestamp is ISO-8601 (e.g. "2025-03-27T14:23:01") — reformat to safe filename pattern
        String timestamp = LocalDateTime.parse(suite.getTimestamp(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String baseName = "suite-result-" + timestamp;

        try {
            Files.createDirectories(REPORTS_DIR);

            Path jsonPath = REPORTS_DIR.resolve(baseName + ".json");
            Path mdPath   = REPORTS_DIR.resolve(baseName + ".md");

            Files.write(jsonPath, jsonGenerator.generate(suite).getBytes(StandardCharsets.UTF_8));
            Files.write(mdPath,   markdownGenerator.generate(suite).getBytes(StandardCharsets.UTF_8));

            return REPORTS_DIR.resolve(baseName);

        } catch (IOException e) {
            throw new RuntimeException("Failed to write suite reports to " + REPORTS_DIR, e);
        }
    }
}
