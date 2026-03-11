package com.modernize.bankbatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the pipeline on a cron schedule.
 *
 * This is the Spring equivalent of a Control-M scheduled job definition.
 * The cron expression is read from batch-pipeline.schedule in application.yml
 * so each environment can have its own schedule without code changes.
 *
 * Not loaded in the sandbox profile — there, BatchRunner (CommandLineRunner)
 * drives the pipeline instead.
 *
 * HOW TO TEST LOCALLY:
 *   Add the "sched" profile, which sets schedule to every minute.
 *   Avoids shell quoting problems with cron expressions on the command line.
 *
 *   cd app && mvn spring-boot:run "-Dspring-boot.run.profiles=dev,sched"
 *
 *   Normal dev run (REST trigger only, no scheduler):
 *   cd app && mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
 */
@Component
@Profile("!sandbox")
public class BatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduler.class);

    private final BatchPipelineService pipelineService;

    public BatchScheduler(BatchPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Scheduled(cron = "${batch-pipeline.schedule}")
    public void scheduledRun() {
        log.info("Scheduled pipeline run triggered");
        try {
            pipelineService.run();
        } catch (IllegalStateException e) {
            log.warn("Scheduled run skipped — pipeline already running");
        } catch (Exception e) {
            log.error("Scheduled pipeline run failed: {}", e.getMessage(), e);
        }
    }
}
