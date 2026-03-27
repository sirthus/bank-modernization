package com.modernize.verificationlab.report;

import com.modernize.verificationlab.model.VerificationResult;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders a VerificationResult to a human-readable HTML report.
 *
 * Uses Thymeleaf with ClassLoaderTemplateResolver — no Spring web context
 * required. The template lives at src/main/resources/templates/verification-report.html
 * and is resolved from the classpath at runtime.
 *
 * The HTML report is the reviewer-facing artifact. Its first screen must answer
 * the primary question immediately: did this run pass, and if not, why not?
 */
public class HtmlReportGenerator {

    private static final SpringTemplateEngine ENGINE = buildEngine();

    private HtmlReportGenerator() {}

    public static void write(VerificationResult result, Path outputPath) throws IOException {
        Context context = new Context();
        context.setVariable("result", result);

        String html = ENGINE.process("verification-report", context);
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    private static SpringTemplateEngine buildEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
