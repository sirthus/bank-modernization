package com.modernize.contractreplay;

import com.modernize.bankbatch.BankBatchApplication;
import com.modernize.contractreplay.replay.ReplayRunner;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.batch.core.Job;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for all replay and invariant tests that need a live database.
 *
 * Provides:
 *   - Spring context wired from BankBatchApplication (the real pipeline beans)
 *   - Testcontainers PostgreSQL via the contracttest profile datasource URL
 *   - All four job beans injected and available to subclasses
 *   - @BeforeEach cleanState() — wipes bank.* tables and re-seeds reference
 *     data before every test so no test can leak state into the next
 *
 * Subclasses inherit the autowired fields directly and call
 * ReplayRunner.runFullPipeline() with the injected jobs and use the returned
 * ReplayRun metadata when assertions need to be scoped to a specific run.
 *
 * webEnvironment = NONE — no Tomcat started here. ApiContractTest overrides
 * this with RANDOM_PORT in its own @SpringBootTest annotation.
 */
@SpringBatchTest
@SpringBootTest(classes = BankBatchApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("contracttest")
public abstract class AbstractContractReplayTest {

    @Autowired protected JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired protected JobRepositoryTestUtils jobRepositoryTestUtils;
    @Autowired protected JdbcTemplate jdbcTemplate;

    @Autowired @Qualifier("loadTransactionsJob")     protected Job loadTransactionsJob;
    @Autowired @Qualifier("validateTransactionsJob") protected Job validateTransactionsJob;
    @Autowired @Qualifier("postTransactionsJob")     protected Job postTransactionsJob;
    @Autowired @Qualifier("reconcileJob")            protected Job reconcileJob;

    @BeforeEach
    void cleanState() {
        if (shouldCleanStateBeforeEach()) {
            ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
        }
    }

    protected boolean shouldCleanStateBeforeEach() {
        return true;
    }
}
