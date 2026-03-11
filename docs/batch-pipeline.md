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
| loadStep | Chunk (500) | FlatFileItemReader -> processor sets batchId -> JdbcBatchItemWriter into staged_transactions |

The load job uses `@StepScope` on the reader and setup tasklet so that
each file gets its own instances with the correct filename parameter.

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
