package com.modernize.bankbatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchScheduler.
 *
 * Pure unit test — no Spring context. The scheduler's only job is to call
 * pipelineService.run() and absorb exceptions gracefully (so a failed run
 * does not crash the scheduler thread and kill future scheduled invocations).
 *
 * We verify:
 *   1. Happy path delegates to the service.
 *   2. IllegalStateException (already running) is swallowed — not rethrown.
 *   3. Any other exception is swallowed — not rethrown.
 */
class BatchSchedulerTest {

    private BatchPipelineService pipelineService;
    private BatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        pipelineService = mock(BatchPipelineService.class);
        scheduler = new BatchScheduler(pipelineService);
    }

    /**
     * Normal case: scheduled trigger calls the pipeline service once.
     */
    @Test
    void scheduledRun_delegatesToPipelineService() throws Exception {
        scheduler.scheduledRun();

        verify(pipelineService, times(1)).run();
    }

    /**
     * Already-running case: IllegalStateException must be absorbed.
     *
     * If the scheduler fires while a previous run is still in progress, the
     * run() guard throws IllegalStateException. The scheduler must log a warning
     * and return normally — not propagate the exception, which would prevent
     * future scheduled invocations from firing.
     */
    @Test
    void scheduledRun_whenAlreadyRunning_doesNotThrow() throws Exception {
        doThrow(new IllegalStateException("Pipeline is already running"))
            .when(pipelineService).run();

        // Must not throw
        scheduler.scheduledRun();
    }

    /**
     * Pipeline failure: any other exception must be absorbed.
     *
     * If the pipeline crashes (reconciliation failure, DB outage, etc.), the
     * scheduler must log the error and return normally so the next scheduled
     * window can still fire.
     */
    @Test
    void scheduledRun_whenPipelineFails_doesNotThrow() throws Exception {
        doThrow(new RuntimeException("Pipeline halted: reconciliation failed"))
            .when(pipelineService).run();

        // Must not throw
        scheduler.scheduledRun();
    }
}
