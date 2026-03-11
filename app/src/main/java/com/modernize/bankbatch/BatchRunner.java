package com.modernize.bankbatch;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sandbox-only entry point.
 *
 * In the sandbox profile the app still behaves like a classic batch job:
 * start up, run the pipeline, exit. This mirrors how Control-M submits
 * jobs in production — one invocation, one run.
 *
 * In dev/test/prod this class is not loaded. The pipeline is driven
 * by BatchScheduler (@Scheduled) or BatchController (REST) instead.
 */
@Component
@Profile("sandbox")
public class BatchRunner implements CommandLineRunner {

    private final BatchPipelineService pipelineService;

    public BatchRunner(BatchPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public void run(String... args) throws Exception {
        pipelineService.run();
    }
}
