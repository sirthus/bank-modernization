# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All Maven commands run from the `app/` directory.

### Preferred commands by branch

```bash
# Development work on dev branch / dev database

cd app && mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

# Verification work on test branch / test database

cd app && mvn spring-boot:run "-Dspring-boot.run.profiles=test"

# Production-aligned verification only after promotion readiness

cd app && mvn spring-boot:run "-Dspring-boot.run.profiles=prod"

# Run tests

cd app && mvn test

# Build without running

cd app && mvn package
```

### Scratch/local-only command

```bash
# Sandbox is disposable local scratch only; do not use it for branch-based promotion work
cd app && mvn spring-boot:run
```

PowerShell environment scripts (run from repo root):

```powershell
# Start PostgreSQL
docker compose up -d

# Create/rebuild all four databases
.\scripts\setup-environments.ps1

# Reset one database from scratch
.\scripts\reset-env.ps1 -Database modernize_buildtest

# Verify one database
.\scripts\verify-env.ps1 -Database modernize_test
```

## Architecture

This is a Spring Boot / Spring Batch application that simulates a bank's inbound ACH transaction processing pipeline.

### Pipeline sequence

`BatchPipelineService` orchestrates four jobs in order:

1. **Load** (`loadTransactionsJob`) — reads each CSV listed under `batch-pipeline.files` in `application.yml` into `bank.staged_transactions` (status: `staged`). Runs once per file.

2. **Validate** (`validateTransactionsJob`) — partitions staged records by `batch_id`, applies business rules, marks records `validated` or `rejected`. Uses a skip policy tolerating up to 10,000 `ValidationException`s.

3. **Post** (`postTransactionsJob`) — partitions validated records by `batch_id`, inserts into `bank.transactions`, updates staged status to `posted`.

4. **Reconcile** (`reconcileJob`) — compares staged-posted counts/totals against production; writes results to `bank.batch_reconciliations`.

5. **Summary report** — printed to console and saved to `app/reports/batch_report_YYYYMMDD_HHmmss.txt`.

### Partitioning

Validate and Post use `BatchIdPartitioner`, which creates one Spring Batch partition per `batch_id`. Worker steps are `@StepScope` beans that pull `batchId` from the step execution context so each worker queries only its own partition.

### Key classes

| Class                           | Location                  | Role                                                            |
| ------------------------------- | ------------------------- | --------------------------------------------------------------- |
| `BatchPipelineService`          | `com.modernize.bankbatch` | Owns pipeline logic; called by all three triggers               |
| `BatchRunner`                   | `com.modernize.bankbatch` | Sandbox-only CommandLineRunner; delegates to BatchPipelineService |
| `BatchScheduler`                | `com.modernize.bankbatch` | @Scheduled trigger (non-sandbox); cron from batch-pipeline.schedule |
| `BatchController`               | `com.modernize.bankbatch` | REST trigger (non-sandbox); POST /api/batch/run, GET /api/batch/status |
| `BatchPipelineProperties`       | `com.modernize.bankbatch` | Binds `batch-pipeline.files` and `batch-pipeline.schedule`      |
| `LoadTransactionsJobConfig`     | `job/`                    | Defines load job, setup tasklet, CSV reader, staging writer     |
| `ValidateTransactionsJobConfig` | `job/`                    | Defines validate job with partitioned master/worker steps       |
| `PostTransactionsJobConfig`     | `job/`                    | Defines post job with partitioned master/worker steps           |
| `ReconcileJobConfig`            | `job/`                    | Defines reconcile job (tasklet-based)                           |
| `BatchIdPartitioner`            | `partitioner/`            | Builds one partition per batch_id                               |
| `ValidationProcessor`           | `processor/`              | Applies business rules; throws `ValidationException` on failure |
| `ValidationSkipListener`        | `listener/`               | Marks rejected records; logs to `bank.batch_job_errors`         |

### Inbound files

CSV files are loaded from the classpath (`src/main/resources/`). The list of files to process is configured in `application.yml` under `batch-pipeline.files`.

### Environments and profiles

| Profile             | Database              | Branch             | Disposable |
| ------------------- | --------------------- | ------------------ | ---------- |
| `sandbox` (default) | `modernize_buildtest` | local scratch only | Yes        |
| `dev`               | `modernize_dev`       | `dev`              | No         |
| `test`              | `modernize_test`      | `test`             | No         |
| `prod`              | `modernize_prod`      | `main`             | No         |

Profile-specific JDBC config lives in `app/src/main/resources/application-{profile}.yml`.

## Contract-Replay-Suite

A standalone Maven module (`contract-replay-suite/`) that validates the batch pipeline against machine-readable contracts and executes operational regression scenarios. It requires no manual database setup — Testcontainers starts a fresh PostgreSQL 15 container automatically.

### Running locally

```bash
# Run all contract and replay tests (no Docker Compose needed)
./app/mvnw -f contract-replay-suite/pom.xml test
```

The suite uses the `contracttest` Spring profile, which configures a Testcontainers JDBC URL. Reports are written to `contract-replay-suite/reports/` (git-ignored). CI uploads them as artifacts on every PR run.

### What the suite covers

| Layer | What it checks |
| ----- | -------------- |
| File contracts | ACH CSV column presence, types, and drift classification |
| API contracts | `/api/batch/run` and `/api/batch/status` response shape and status codes |
| Output invariants | Six reconciliation invariants (counts, totals, rejected exclusion) |
| Replay scenarios | Three end-to-end regression scenarios (RP-001 baseline, RP-002 run isolation, RP-003 validation skip) |

Contract definitions live in `contract-replay-suite/src/main/resources/contracts/`. Replay fixture expectations live in `contract-replay-suite/src/test/resources/fixtures/`. Sample evidence reports are checked in under `contract-replay-suite/docs/examples/`.

## Workflow rules

Claude must follow this repository workflow exactly:

1. Development happens on `dev`.
   
   - Do feature work, bug fixes, refactors, and normal coding on `dev`.
   
   - Use the `dev` Spring profile and `modernize_dev` database when validating branch work.
   
   - Do not do normal implementation work directly on `test` or `main`.

2. Verification happens on `test`.
   
   - Promote code from `dev` to `test` for verification.
   
   - On `test`, use the `test` Spring profile and `modernize_test` database.
   
   - Only verification, stabilization, and release-readiness fixes belong here.

3. Promotion happens on `main`.
   
   - Only code already verified on `test` should be promoted to `main`.
   
   - `main` maps to the `prod` profile and `modernize_prod`.
   
   - Do not make ad hoc feature changes directly on `main`.

4. Safety rules Claude must follow.
   
   - Before making changes, check and state the current Git branch.
   
   - Before running the app, use the profile that matches the current branch.
   
   - If branch and profile do not match, stop and explain the mismatch.
   
   - Use pull requests for all branch promotion: `dev -> test`, then `test -> main`
   
   - Do not push directly to `test` or `main`.
   
   - Assume `test` and `main` are protected branches unless the user explicitly says otherwise.

### Promotion flow

```text
dev -> test -> main
```

Working rule: build on `dev`, verify on `test`, promote to `main`.

### Git operating assumptions

- Default working branch for Claude development tasks: `dev`

- Default verification branch: `test`

- Default release branch: `main`

- If asked to implement a change and no branch is specified, assume `dev`

- If asked to verify promotion readiness, assume `test`

- If asked to prepare for release, assume the code has already passed through `test`

### CI expectation

PRs into `test` and `main` must pass `mvn test`.

### Database schema

The `bank` schema is built by SQL files in `sql/` (numbered `001_` through `012_`). Key tables: `batch_jobs`, `transaction_batches`, `staged_transactions`, `batch_job_errors`, `batch_reconciliations`, `transactions`, `accounts`.

Spring Batch also maintains its own metadata tables (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, etc.) initialized via `spring.batch.jdbc.initialize-schema: always`.
