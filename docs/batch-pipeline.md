# Batch Pipeline Architecture

This document describes the Spring Batch pipeline that processes
inbound transaction files.

## Overview

The pipeline runs four jobs in sequence: load, validate, post, reconcile.
Each job creates a control record in `bank.batch_jobs` to track its
execution. `BatchPipelineService` owns the orchestration logic and is
called by whichever trigger is active for the current profile.

## Data flow

    Inbound CSV file
        |
        v
    [Load Job]
        |-- creates batch_jobs row (load_transactions)
        |-- creates transaction_batches row (file_name, status: received)
        |-- reads CSV, inserts into staged_transactions (status: staged)
        v
    [Validate Job]
        |-- creates batch_jobs row (validate_transactions)
        |-- partitions staged records by batch_id
        |-- applies business rules in worker steps
        |-- good records: status -> validated
        |-- bad records: status -> rejected, error logged to batch_job_errors
        v
    [Post Job]
        |-- creates batch_jobs row (post_transactions)
        |-- partitions validated records by batch_id
        |-- posts validated records in worker steps
        |-- staged status -> posted
        v
    [Reconcile Job]
        |-- creates batch_jobs row (reconcile_batches)
        |-- compares staged posted counts/totals vs production
        |-- records results in batch_reconciliations
        v
    [Summary Report]
        |-- scoped to the current run (filters by run start timestamp)
        |-- prints to console
        |-- saves to app/reports/batch_report_YYYYMMDD_HHmmss.txt

## Triggering

The pipeline can be triggered three ways. Which trigger is active depends
on the Spring profile.

### Sandbox (CommandLineRunner)

`BatchRunner` implements `CommandLineRunner` and is loaded only in the
`sandbox` profile. The app starts, runs the pipeline once, and exits.
This mirrors how Control-M submits a classic batch job: one invocation,
one run, process exits when done.

    cd app && mvn spring-boot:run

### Scheduled (@Scheduled)

`BatchScheduler` is loaded in all non-sandbox profiles. It fires the
pipeline on the cron expression configured in `batch-pipeline.schedule`.
The default schedule (set in `application.yml`) is nightly at 2:00 AM.

The schedule uses Spring's 6-field cron format (second, minute, hour,
day-of-month, month, day-of-week) — not the standard 5-field Unix cron.
For example, `"0 0 2 * * ?"` means: at second 0, minute 0, hour 2, every
day. The `sched` profile overrides this to `"0 * * * * ?"` (every minute)
for local testing.

The scheduler is single-threaded. If a run takes longer than the
schedule interval, the next fire is delayed until the current run
finishes — the same behavior as a Control-M job fence.

To test scheduling locally without editing yml files, add the `sched`
profile, which overrides the schedule to every minute:

    cd app && mvn spring-boot:run "-Dspring-boot.run.profiles=dev,sched"

### REST endpoint

`BatchController` exposes two endpoints, available in all non-sandbox
profiles:

| Method | Path | Description |
|---|---|---|
| POST | `/api/batch/run` | Triggers the pipeline immediately. Blocks until complete. Returns 409 if already running. |
| GET | `/api/batch/status` | Returns `running` or `idle`. |

This is the Control-M "force run" equivalent — useful for reruns,
manual testing, and integration with other systems.

    curl -X POST http://localhost:8080/api/batch/run
    curl       http://localhost:8080/api/batch/status

### Concurrent run guard

`BatchPipelineService` uses an `AtomicBoolean` to prevent two triggers
from running the pipeline simultaneously. If the scheduler fires while
a REST-triggered run is in progress, the scheduled run is skipped and
logged as a warning. The REST endpoint returns HTTP 409.

## Jobs and steps

### Load (loadTransactionsJob)

Runs once per inbound file. Accepts `fileName` as a job parameter.

| Step | Type | What it does |
|---|---|---|
| setupStep | Tasklet | Creates batch_jobs and transaction_batches rows, stores IDs in ExecutionContext |
| loadStep | Chunk (500) | FlatFileItemReader → processor sets batchId → JdbcBatchItemWriter into staged_transactions |

All chunk-oriented steps (load, validate, post) use a chunk size of 500 items.
Each chunk is wrapped in a transaction, so a failure rolls back only the current
chunk, not the entire job.

The load job uses `@StepScope` on the reader and setup tasklet so that
each file gets its own instances with the correct filename parameter.

`staged_transactions.account_id` carries no foreign key constraint.
Staging is the raw intake layer — records with unknown account IDs load
successfully and are caught by Rule 3 during the validate job. Enforcing
referential integrity at the staging level would make those failure cases
impossible to load and therefore impossible to test.

### Validate (validateTransactionsJob)

Runs once, processing staged records from all loaded files.

| Step | Type | What it does |
|---|---|---|
| validateSetupStep | Tasklet | Creates batch_jobs row |
| validateStep | Partitioned master step | Builds one partition per `batch_id` with staged records |
| validateWorkerStep | Chunk (500) | Reads one partition's staged records, validates them, writes updates |

Validation rules:
- amount_cents must be positive
- direction must be D or C
- account must exist
- account must be active

The validate worker step uses a **skip policy**: when the processor
throws a `ValidationException`, Spring Batch skips the record
(up to 10,000 skips) and the `ValidationSkipListener` marks it rejected
and logs the error.

### Post (postTransactionsJob)

Runs once, posting all validated records.

| Step | Type | What it does |
|---|---|---|
| postSetupStep | Tasklet | Creates batch_jobs row |
| postStep | Partitioned master step | Builds one partition per `batch_id` with validated records |
| postWorkerStep | Chunk (500) | Reads one partition's validated records and posts them |

The `PostingWriter` inserts each record into the production
`transactions` table and updates the staged status to `posted`.

### Reconcile (reconcileJob)

Runs once, checking all batches.

| Step | Type | What it does |
|---|---|---|
| reconcileStep | Tasklet | Queries staged vs production counts/totals per batch, writes to batch_reconciliations |

Reconciliation matches staged posted records to production by
account_id, merchant_id, direction, and amount_cents.

## Partitioning

The validate and post jobs now use partitioned steps instead of async
chunking over a shared reader.

`BatchIdPartitioner`:
- queries distinct `batch_id` values from `bank.staged_transactions`
- creates one Spring Batch partition per batch
- stores `batchId` in each partition's `ExecutionContext`

The worker readers are `@StepScope` beans that pull `batchId` from the
step execution context and query only their assigned partition.

This design keeps each worker step isolated to one batch while still
allowing parallel execution through the partitioned master step.

## Listeners

| Listener | Attached to | Purpose |
|---|---|---|
| LoadJobCompletionListener | Load job | Updates batch_jobs and transaction_batches with final status and counts |
| ValidateJobCompletionListener | Validate job | Updates batch_jobs with final status and counts |
| PostJobCompletionListener | Post job | Updates batch_jobs with final status and counts |
| ReconcileJobCompletionListener | Reconcile job | Updates batch_jobs on failure |
| ValidationSkipListener | Validate worker step | Marks rejected records, logs errors to batch_job_errors |
| ProgressListener | Load, validate, post worker steps | Logs read/written/skipped counts after each chunk |

## Observability

### Health endpoints

The following health endpoints are available in non-sandbox profiles:

| Endpoint | Group members | Purpose |
|---|---|---|
| `/actuator/health` | All contributors | Full view — DB, disk, liveness, readiness |
| `/actuator/health/liveness` | `livenessState` | Is the JVM process alive? Used for OpenShift liveness probe |
| `/actuator/health/readiness` | `readinessState`, `db` | Is the app ready and the database reachable? Used for OpenShift readiness probe |

### Prometheus metrics

`/actuator/prometheus` exposes all metrics in Prometheus exposition format.
A Prometheus server scrapes this endpoint on a schedule; Grafana queries
Prometheus to render dashboards and fire alerts.

Key Spring Batch metrics available after a pipeline run:

| Metric | What it measures |
|---|---|
| `spring_batch_job_seconds` | Duration per job, labelled by job name and status |
| `spring_batch_step_seconds` | Duration per step, labelled by job, step name, and status |
| `spring_batch_item_process_seconds` | Item processing time, labelled by status (`SUCCESS` / `FAILURE`) |
| `spring_batch_job_launch_count_total` | Total job launches since startup |

### Structured logging

Log output format depends on the active profile:

| Profile | Format |
|---|---|
| `sandbox`, `batchtest` | Plain text (readable in local console and test output) |
| `dev`, `test`, `prod` | JSON (Logstash format — suitable for Splunk, ELK, Datadog) |

`BatchPipelineService` sets two MDC fields at the start of each pipeline run:

| Field | Value | Scope |
|---|---|---|
| `pipeline.runId` | Epoch millis of the run start timestamp | Entire pipeline run |
| `job.name` | Name of the current job being launched | Per-job |

Because MDC is thread-local and all jobs run synchronously on the same thread,
these fields appear on every log line — including framework-level Spring Batch
logs — for the duration of the run. In a log aggregator, filtering by
`pipeline.runId` returns the complete trace of a single pipeline run across
all classes.

## Error handling

- **Skip policy**: the validate worker step tolerates up to 10,000
  `ValidationException` skips before aborting.
- **Job completion listeners**: detect failed jobs and update batch_jobs
  status.
- **Restartability**: Spring Batch tracks job state in its metadata
  tables. Partitioning avoids the shared-reader restart warnings seen
  with the earlier async chunking approach.

## Control tables

| Table | Purpose |
|---|---|
| batch_jobs | Tracks each job execution (name, status, timing, record count) |
| transaction_batches | Tracks each inbound file (job link, file name, record count, status) |
| batch_job_errors | Captures validation errors (job link, message, record reference) |
| staged_transactions | Holds inbound records through the pipeline (staged -> validated/rejected -> posted) |
| batch_reconciliations | Stores reconciliation results per batch (counts, totals, match flags) |

Spring Batch also maintains its own metadata tables
(`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, and related tables)
which track job state for restartability.

## Test CSV files

Six test CSV files live in `app/src/main/resources/` alongside the
production ACH files. They are used during development and integration
testing to exercise specific validation scenarios without relying on
the full production data set.

| File | Scenario |
|---|---|
| `test_all_valid.csv` | Three records that pass all validation rules |
| `test_invalid_amounts.csv` | Two zero/negative amounts (Rule 1) + one valid |
| `test_invalid_directions.csv` | Three invalid direction values (Rule 2) |
| `test_unknown_account.csv` | One record with account 9999, which does not exist (Rule 3) |
| `test_inactive_account.csv` | One record with account 2004, which is frozen (Rule 4) |
| `test_mixed.csv` | Five records covering valid, zero amount, unknown account, and invalid direction |

### Switching between ACH and test files

`application.yml` controls which files the pipeline processes. Comment
out the set you do not want to run:

    batch-pipeline:
      files:
        # ACH files (production data)
        #- ach_20250310.csv
        #- ach_20250317.csv
        #- ach_20250324.csv
        # Test files (comment out ACH above, uncomment these)
        - test_all_valid.csv
        - test_invalid_amounts.csv
        - test_invalid_directions.csv
        - test_unknown_account.csv
        - test_inactive_account.csv
        - test_mixed.csv

Integration tests do not use this list — they call the load job directly
with a specific `fileName` parameter via `JobParametersBuilder`, so
`application.yml` does not affect them.

## Summary report

The summary report is scoped to the current pipeline run. All queries
filter to `bank.batch_jobs` rows with `started_at >= runSince`, where
`runSince` is the timestamp captured at the start of
`BatchPipelineService.run()`. Totals, file summaries, rejection reasons,
and reconciliation results all cascade from that anchor, so a database
with history from previous runs produces a clean, accurate report for
the run just completed.

Reports are printed to the console and saved to
`app/reports/batch_report_YYYYMMDD_HHmmss.txt` (excluded from git).
