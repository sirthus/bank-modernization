# Bank Modernization

A simulated bank batch processing system built with **Java 21, Spring Boot 3.4, Spring Batch, and PostgreSQL 18**, modeling the kind of COBOL-to-Java migration happening across government and financial services. The project implements a complete inbound ACH transaction pipeline — from CSV intake through validation, posting, and reconciliation — with partitioned parallel execution, skip-based error handling, and a full integration test suite.

## Why this project exists

Large organizations (IRS, banks, insurers) are migrating decades-old mainframe batch systems to modern Java/Spring stacks. This project is a hands-on implementation of that migration pattern: replacing a sequential COBOL batch pipeline with Spring Batch jobs that preserve the same processing guarantees — audit trails, reconciliation, error isolation — while gaining observability, testability, and concurrency.

## Pipeline

The pipeline runs four jobs in sequence, orchestrated by `BatchPipelineService`:

```
Inbound CSV  →  Load  →  Validate  →  Post  →  Reconcile  →  Summary Report
```

1. **Load** — reads CSV files into `staged_transactions` (status: `staged`). One run per file.
2. **Validate** — partitions staged records by `batch_id`, applies business rules. Good records become `validated`; bad records become `rejected` with errors logged to `batch_job_errors`.
3. **Post** — partitions validated records, inserts into the production `transactions` table, updates staged status to `posted`.
4. **Reconcile** — compares staged-posted counts and totals against production per batch. Records results in `batch_reconciliations`. If any batch doesn't balance, the pipeline halts before generating the summary report.

Validate and Post use `BatchIdPartitioner` for concurrent execution — one Spring Batch partition per batch, each worker scoped to its own data.

### Validation rules

| Rule | Check | On failure |
|------|-------|------------|
| 1 | `amount_cents` must be positive | Rejected |
| 2 | `direction` must be `D` or `C` | Rejected |
| 3 | Account must exist | Rejected |
| 4 | Account must be active (not frozen/closed) | Rejected |

Errors accumulate per record — a record that fails Rules 1 and 2 gets both messages in a single rejection, not two separate passes.

### Triggering

| Method | Profile | Behavior |
|--------|---------|----------|
| `CommandLineRunner` | `sandbox` | Runs once and exits (mirrors Control-M job submission) |
| `@Scheduled` cron | `dev`, `test`, `prod` | Nightly at 2:00 AM (configurable) |
| REST endpoint | `dev`, `test`, `prod` | `POST /api/batch/run` with concurrency guard |

## Verification Lab

A companion Maven module that proves the modernized pipeline produces acceptable outputs by running golden datasets through the full pipeline, comparing actual database state against legacy-style baselines field-by-field, and producing HTML and JSON evidence reports. CI fails on unapproved divergence. Accepted differences require a signed, checked-in reviewer entry — not a suppression flag.

Three datasets cover normalization noise (PASS), monetary precision drift (FAIL), and behavioral divergence on record disposition (FAIL). See [`verification-lab/README.md`](verification-lab/README.md) to run it, read reports, and understand the approval workflow. See [`docs/verification-case-study.md`](docs/verification-case-study.md) for the engineering rationale.

## Testing

**25+ tests across 6 test classes**, using Spring Batch Test, Testcontainers (real PostgreSQL), and AssertJ:

| Test class | Scope | What it covers |
|-----------|-------|----------------|
| `ValidationProcessorTest` | Unit | All 4 rules, boundary values, null handling, multi-rule error accumulation |
| `LoadTransactionsJobTest` | Integration | CSV staging, record counts, status after load |
| `ValidateTransactionsJobTest` | Integration | Each rule in isolation, mixed valid/invalid batches, error logging |
| `PostTransactionsJobTest` | Integration | Selective posting, data correctness, rejected record isolation, no-op handling |
| `ReconcileJobTest` | Integration | Count matches, total mismatches, duplicate detection, rejected exclusion |
| `FullPipelineTest` | End-to-end | Full 4-job pipeline with all-valid and mixed files; reconciliation failure escalation |

CI runs on every PR via GitHub Actions (`maven-test.yml`): spins up PostgreSQL 18, builds the schema, executes the full test suite.

## Environments

Four databases, four Spring profiles, three-branch promotion model:

| Branch | Profile | Database | Purpose |
|--------|---------|----------|---------|
| `dev` | `dev` | `modernize_dev` | Active development |
| `test` | `test` | `modernize_test` | Promotion verification |
| `main` | `prod` | `modernize_prod` | Production-equivalent |
| local | `sandbox` | `modernize_buildtest` | Disposable rebuild-from-scratch sandbox |

Promotion flow: `dev → test → main`, enforced by branch protection and CI.

## Observability

- **Health probes**: `/actuator/health/liveness` and `/actuator/health/readiness` (OpenShift-ready)
- **Prometheus metrics**: `/actuator/prometheus` — job duration, step duration, item processing time, launch counts
- **Structured logging**: JSON (Logstash format) in dev/test/prod; plain text in sandbox. MDC fields (`pipeline.runId`, `job.name`) on every log line for log aggregator filtering.

## Tech stack

- Java 21, Spring Boot 3.4, Spring Batch
- PostgreSQL 18 (Docker Compose)
- Testcontainers for integration tests
- Micrometer + Prometheus for metrics
- Logstash Logback Encoder for structured logging
- GitHub Actions CI
- PowerShell + Python for environment management and data generation

## Project structure

```
app/
  src/main/java/com/modernize/bankbatch/
    job/            Job configurations (Load, Validate, Post, Reconcile)
    listener/       Job completion listeners, skip listener, progress logging
    partitioner/    BatchIdPartitioner for concurrent execution
    processor/      ValidationProcessor (business rules)
    writer/         ValidationWriter, PostingWriter
    model/          StagedTransaction domain object
    exception/      ValidationException
  src/test/         Integration and unit tests
  src/main/resources/
    application*.yml   Profile-specific configuration
    *.csv              Test and production ACH files

verification-lab/
  datasets/         Three golden datasets (DS-001, DS-002, DS-003) with scenario READMEs
  expected-output/  Legacy-style baseline JSON files, one per dataset
  approved-differences/  Signed reviewer sign-off files (YAML)
  src/              Comparison engine, collector, report generators, integration tests
  reports/          Runtime output — git-ignored; uploaded as CI artifact

sql/                Schema and seed scripts (001–014)
scripts/            PowerShell environment management
docs/               Architecture and environment documentation
```

## Quick start

```bash
# Start PostgreSQL
docker compose up -d

# Create all four databases
.\scripts\setup-environments.ps1

# Run in sandbox mode (processes files and exits)
./app/mvnw -f app/pom.xml spring-boot:run

# Run tests
./app/mvnw -f app/pom.xml test

# Run against dev database
./app/mvnw -f app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The repo includes a Maven wrapper (`app/mvnw`) — no separate Maven installation required.

## Documentation

- [`docs/batch-pipeline.md`](docs/batch-pipeline.md) — full pipeline architecture, job details, error handling, observability
- [`docs/environments.md`](docs/environments.md) — environment matrix, promotion flow, SQL build order
- [`verification-lab/README.md`](verification-lab/README.md) — how to run the verification lab, report format, CI integration
- [`docs/verification-lab-overview.md`](docs/verification-lab-overview.md) — verification design: classification model, normalization rules, approval policy, baseline format
- [`docs/verification-case-study.md`](docs/verification-case-study.md) — engineering narrative: why the lab exists, design decisions, what each dataset demonstrates
