# Batch Pipeline Architecture

This document describes the Spring Batch pipeline that processes
inbound transaction files.

## Overview

The pipeline runs four jobs in sequence: load, validate, post, reconcile.
Each job creates a control record in `bank.batch_jobs` to track its
execution. The `BatchRunner` class orchestrates the sequence.

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
        |-- reads staged records joined with accounts
        |-- applies business rules
        |-- good records: status -> validated
        |-- bad records: status -> rejected, error logged to batch_job_errors
        v
    [Post Job]
        |-- creates batch_jobs row (post_transactions)
        |-- reads validated records
        |-- inserts into production transactions table
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

Runs once, processing all staged records from all loaded files.

| Step | Type | What it does |
|---|---|---|
| validateSetupStep | Tasklet | Creates batch_jobs row |
| validateStep | Chunk (500), 4 threads | SynchronizedItemStreamReader -> ValidationProcessor -> ValidationWriter |

Validation rules:
- amount_cents must be positive
- direction must be D or C
- account must exist
- account must be active

The validate step uses a **skip policy**: when the processor throws a
`ValidationException`, Spring Batch skips the record (up to 10,000 skips)
and the `ValidationSkipListener` marks it rejected and logs the error.

### Post (postTransactionsJob)

Runs once, posting all validated records.

| Step | Type | What it does |
|---|---|---|
| postSetupStep | Tasklet | Creates batch_jobs row |
| postStep | Chunk (500), 4 threads | SynchronizedItemStreamReader -> PostingWriter |

The PostingWriter inserts each record into the production `transactions`
table and updates the staged status to `posted`.

### Reconcile (reconcileJob)

Runs once, checking all batches.

| Step | Type | What it does |
|---|---|---|
| reconcileStep | Tasklet | Queries staged vs production counts/totals per batch, writes to batch_reconciliations |

Reconciliation matches staged posted records to production by
account_id, merchant_id, direction, and amount_cents.

## Concurrency

The validate and post steps use `SimpleAsyncTaskExecutor` with a
concurrency limit of 4. The `JdbcCursorItemReader` is wrapped in a
`SynchronizedItemStreamReader` so that reads are serialized while
processing and writing happen in parallel across threads.

## Listeners

| Listener | Attached to | Purpose |
|---|---|---|
| LoadJobCompletionListener | Load job | Updates batch_jobs and transaction_batches with final status and counts |
| ValidateJobCompletionListener | Validate job | Updates batch_jobs with final status and counts |
| PostJobCompletionListener | Post job | Updates batch_jobs with final status and counts |
| ReconcileJobCompletionListener | Reconcile job | Updates batch_jobs on failure |
| ValidationSkipListener | Validate step | Marks rejected records, logs errors to batch_job_errors |
| ProgressListener | Load, validate, post steps | Logs read/written/skipped counts after each chunk |

## Error handling

- **Skip policy**: the validate step tolerates up to 10,000 ValidationExceptions
  before aborting. Skipped records are marked rejected with error details.
- **Job completion listeners**: detect failed jobs and update batch_jobs status.
- **Restartability**: Spring Batch tracks job state in its metadata tables.
  A failed job can be restarted with the same parameters and will resume
  from where it stopped. The `run.id` date parameter creates unique job
  instances for repeated test runs.

## Control tables

| Table | Purpose |
|---|---|
| batch_jobs | Tracks each job execution (name, status, timing, record count) |
| transaction_batches | Tracks each inbound file (job link, file name, record count, status) |
| batch_job_errors | Captures validation errors (job link, message, record reference) |
| staged_transactions | Holds inbound records through the pipeline (staged -> validated/rejected -> posted) |
| batch_reconciliations | Stores reconciliation results per batch (counts, totals, match flags) |

Spring Batch also maintains its own metadata tables (BATCH_JOB_INSTANCE,
BATCH_JOB_EXECUTION, etc.) which track job state for restartability.
