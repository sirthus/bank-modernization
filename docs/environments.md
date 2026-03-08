# Environment Roles

This document defines the database environments used in this project,
what each one is for, and the rules for working with them.

## Databases

### modernize

Main working database.
Contains the full development dataset.

This database is **not disposable**.
It was populated once with generated sample data and is the primary workspace
for development and exploratory queries.
Do not drop or rebuild this database without deliberate intent.

### modernize_buildtest

Disposable sandbox database.
Used to prove that the SQL scripts in `sql/` can rebuild the schema
and seed data from scratch, and to run the Spring Batch pipeline
against a known clean state.

This database **can be dropped and rebuilt at any time**.
That is its entire purpose.

## Spring profiles

The Spring Boot application uses profiles to select which database to target.
Profile-specific configuration files are in `app/src/main/resources/`.

| Profile | Database | Config file | Usage |
|---|---|---|---|
| `test` (default) | `modernize_buildtest` | `application-test.yml` | Normal development and testing |
| `dev` | `modernize` | `application-dev.yml` | Working with the main dataset |

The base `application.yml` sets the default profile to `test` and holds
shared settings (batch configuration, job auto-launch disabled).

To switch profiles:

    mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

Note: the `dev` database must have all schema tables created before running
the Spring pipeline against it. Use the SQL build scripts to add any missing
tables.

## Scripts and what they target

| Script | Target database | What it does |
|---|---|---|
| `scripts/reset-buildtest.ps1` | `modernize_buildtest` | Drops the database, recreates it, rebuilds schema, loads seed data, verifies |
| `scripts/build-schema.ps1` | `modernize_buildtest` | Runs the SQL build files in order against the sandbox |
| `scripts/verify-buildtest.ps1` | `modernize_buildtest` | Checks tables, counts, joins, batch status, errors, reconciliation |

No script currently targets `modernize` for destructive operations.

## SQL build order

These files are applied in sequence by the build and reset scripts:

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

Primary keys and foreign keys are defined inline in the table creation
scripts. Indexes are kept in a separate file (006) as an independent concern.
Seed data is last so all tables exist before any inserts.

## How data gets into each database

| Database | Population method |
|---|---|
| `modernize` | One-time generated dataset, already loaded |
| `modernize_buildtest` | Schema rebuilt from `sql/` scripts by reset-buildtest.ps1, then Spring Batch pipeline loads inbound CSV data |

## Docker Compose

Both databases are hosted by the same PostgreSQL 18 container
defined in `docker-compose.yml`.
Start with `docker compose up -d` from the repo root.
