# Bank Modernization

Learning project for batch processing modernization using:

- PostgreSQL 18
- Docker / Docker Compose
- Java / Spring Boot / Spring Batch
- PowerShell scripting
- GitHub

## What it does

This project simulates a bank's batch transaction processing pipeline.
Inbound CSV files are loaded into staging, validated against business
rules, posted to a production table, and reconciled. The pipeline is
built in Spring Batch and uses partitioned processing for validate and
post steps.

The pipeline can be triggered three ways, depending on the profile:
- **Sandbox**: runs on startup and exits (like a classic batch job submission)
- **Scheduled**: fires automatically on a cron expression (Control-M equivalent)
- **REST**: triggered on demand via `POST /api/batch/run`

Supporting docs:

- [docs/environments.md](docs/environments.md)
- [docs/batch-pipeline.md](docs/batch-pipeline.md)

## Prerequisites

- Docker Desktop with WSL2
- Java 21+ (tested with JDK 25)
- Apache Maven 3.9+
- PowerShell

## Project structure

    app/                    Spring Boot / Spring Batch application
    docs/                   design documents
    sample-data/            inbound CSV files (canonical copies)
    scripts/                PowerShell and Python helper scripts
    sql/                    schema and seed SQL files
    docker-compose.yml      PostgreSQL container definition

## Quick start

Start PostgreSQL:

    docker compose up -d

Create or rebuild all environments:

    .\scripts\setup-environments.ps1

Reset one environment:

    .\scripts\reset-env.ps1 -Database modernize_buildtest

Verify one environment:

    .\scripts\verify-env.ps1 -Database modernize_test

Run the pipeline (sandbox — runs on startup and exits):

    cd app
    mvn spring-boot:run

Pipeline reports are saved to `app/reports/batch_report_YYYYMMDD_HHmmss.txt`
(directory is created on first run; excluded from git).

Run as a long-running server (dev — REST trigger, no auto-schedule):

    cd app
    mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
    curl -X POST http://localhost:8080/api/batch/run
    curl       http://localhost:8080/api/batch/status

Run as a long-running server with per-minute scheduling (dev + sched):

    cd app
    mvn spring-boot:run "-Dspring-boot.run.profiles=dev,sched"

Run against test or prod profiles:

    mvn spring-boot:run "-Dspring-boot.run.profiles=test"
    mvn spring-boot:run "-Dspring-boot.run.profiles=prod"

## Environments

This repo uses four Spring profiles and four databases:

- `sandbox` -> `modernize_buildtest`
- `dev` -> `modernize_dev`
- `test` -> `modernize_test`
- `prod` -> `modernize_prod`

For details on environments, scripts, and promotion flow, see
[docs/environments.md](docs/environments.md).

## Branch flow

Changes move through:

    dev -> test -> main

Work is done on `dev`, verified on `test`, then promoted to `main`.

## Monitoring endpoints

Available when running with any non-sandbox profile (`dev`, `test`, `prod`):

| Endpoint | Description |
|---|---|
| `GET /api/batch/status` | Returns `running` or `idle` |
| `POST /api/batch/run` | Triggers the pipeline immediately |
| `GET /actuator/health` | Full health — all contributors |
| `GET /actuator/health/liveness` | Liveness group — JVM process health (used by OpenShift liveness probe) |
| `GET /actuator/health/readiness` | Readiness group — application ready + database reachable (used by OpenShift readiness probe) |
| `GET /actuator/metrics` | List of all metric names |
| `GET /actuator/prometheus` | Prometheus scrape endpoint — all metrics in exposition format |

## Logging

In `sandbox` and `batchtest` profiles, logs are plain text. In `dev`, `test`, and `prod`, logs
are structured JSON (Logstash format) suitable for ingestion by Splunk, ELK, or Datadog.

Each pipeline run sets `pipeline.runId` and `job.name` as MDC fields, so every log line
emitted during a run can be filtered by run or by job in a log aggregator.

## Testing

Run the full test suite:

    cd app && mvn test

Integration tests connect to `modernize_buildtest` via the `batchtest`
profile. The database must be running before `mvn test` is executed.

### Test inventory

| Test class | Type | What it covers |
|---|---|---|
| `ValidationProcessorTest` | Unit | All four validation rules, boundary values, multi-rule accumulation, exception traceability |
| `LoadTransactionsJobTest` | Integration | Load job stages all records regardless of validity; record counts per file; all records arrive with `staged` status |
| `ValidateTransactionsJobTest` | Integration | All-valid happy path; invalid amounts; unknown account; inactive account; mixed batch counts and error messages |
| `PostTransactionsJobTest` | Integration | Validated records posted and marked `posted`; rejected records untouched; column value correctness; empty database edge case |
| `ReconcileJobTest` | Integration | Happy path (counts and totals match); missing transaction; wrong amount; extra transaction (duplicate post); rejected records correctly excluded from staged count |
| `FullPipelineTest` | End-to-end | Full Load → Validate → Post → Reconcile sequence against real CSVs; asserts final state across all four tables; reconciliation failure detection when a transaction is deleted after posting |

### Test types in use

**Unit tests** (`ValidationProcessorTest`) instantiate the class under test
directly with `new` — no Spring context, no database. They run in under a
second and are the first line of defence for logic defects.

**Integration tests** (`LoadTransactionsJobTest`, `ValidateTransactionsJobTest`,
`PostTransactionsJobTest`, `ReconcileJobTest`) use `@SpringBatchTest` +
`@SpringBootTest` with `@ActiveProfiles("batchtest")`. They load the full
application context and run against `modernize_buildtest`. Each test class uses
`@BeforeEach` to clear staged data so tests are independent.

**End-to-end tests** (`FullPipelineTest`) run all four jobs in sequence using
real CSV files from the classpath. No data is pre-inserted — the load job
produces what validate expects, validate produces what post expects, and so on.
These tests verify the hand-offs between jobs and the final state across all
four bank schema tables.

## Scripts

- `scripts/setup-environments.ps1` - creates and builds all environments
- `scripts/reset-env.ps1` - resets one named environment
- `scripts/verify-env.ps1` - verifies one named environment
- `scripts/reset-buildtest.ps1` - sandbox-only convenience reset
- `scripts/verify-buildtest.ps1` - sandbox-only convenience verify
- `scripts/build-schema.ps1` - runs SQL files in order against a target database
- `scripts/generate_data.py` - generates seed SQL and inbound CSV files
