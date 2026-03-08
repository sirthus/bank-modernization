# Bank Modernization

Learning project for batch processing modernization using:

- PostgreSQL 18
- Docker / Docker Compose
- Java / Spring Boot / Spring Batch
- PowerShell scripting
- GitHub

## What it does

This project simulates a bank's batch transaction processing pipeline.
Inbound CSV files containing transactions are loaded into a staging area,
validated against business rules, posted to a production table, and
reconciled. The pipeline is built in Spring Batch with multi-threaded
processing and produces a summary report after each run.

For pipeline architecture details, see [docs/batch-pipeline.md](docs/batch-pipeline.md).

## Prerequisites

- Docker Desktop with WSL2
- Java JDK 17+ (tested with JDK 25)
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

Reset and rebuild the sandbox database. This drops and recreates
`modernize_buildtest`, then runs the SQL files listed under
**SQL build order** below in sequence to create the schema and load
seed data. It finishes by running the verify script to confirm
everything built correctly:

    .\scripts\reset-buildtest.ps1

Verify the database state at any time:

    .\scripts\verify-buildtest.ps1

Run the Spring Batch pipeline:

    cd app
    mvn spring-boot:run

The pipeline loads three inbound CSV files (150,000 transactions),
validates them, posts the good ones, reconciles, and prints a summary
report. A timestamped report file is saved to `app/reports/`.

## Databases and environments

- `modernize` - main working database
- `modernize_buildtest` - disposable sandbox, rebuilt by reset-buildtest.ps1

The Spring Boot application uses profiles to select which database to
target. Default is `test` (sandbox). See
[docs/environments.md](docs/environments.md) for details.

## SQL build order

These files are run in sequence by `scripts/build-schema.ps1`, which is
called by `scripts/reset-buildtest.ps1`:

    001_create_schema.sql
    002_create_customers.sql
    003_create_accounts.sql
    004_create_merchants.sql
    005_create_transactions.sql
    006_create_indexes.sql
    007_create_batch_jobs.sql
    008_create_transaction_batches.sql
    009_create_batch_job_errors.sql
    010_create_staged_transactions.sql
    011_create_batch_reconciliations.sql
    012_seed_data.sql

## Scripts

- `scripts/reset-buildtest.ps1` - drops and rebuilds the sandbox database
- `scripts/build-schema.ps1` - runs SQL files in order against the sandbox
- `scripts/verify-buildtest.ps1` - verifies database state
- `scripts/generate_data.py` - generates seed SQL and inbound CSV files
