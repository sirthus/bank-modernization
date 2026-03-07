\# Environment Roles



This document defines the database environments used in this project,

what each one is for, and the rules for working with them.



\## Databases



\### modernize



Main working database.

Contains the full development dataset.



This database is \*\*not disposable\*\*.

It was populated once with generated sample data and is the primary workspace

for development and exploratory queries.

Do not drop or rebuild this database without deliberate intent.



No automated scripts currently target this database for destructive operations.



\### modernize\_buildtest



Disposable sandbox database.

Used to prove that the SQL scripts in `sql/` can rebuild the schema

and seed data from scratch.



Contains a small, controlled seed set.



This database \*\*can be dropped and rebuilt at any time\*\*.

That is its entire purpose.



\## Scripts and what they target



| Script | Target database | What it does |

|---|---|---|

| `scripts/reset-buildtest.ps1` | `modernize\_buildtest` | Drops the database, recreates it, rebuilds the schema, loads seed data |

| `scripts/verify-buildtest.ps1` | `modernize\_buildtest` | Checks table existence, row counts, and join integrity |

| `scripts/build-schema.ps1` | `modernize\_buildtest` | Runs the SQL build files in order against the sandbox |



No script currently targets `modernize`.



\## SQL build order



These files are applied in sequence by the build and reset scripts:



1\. `001\_create\_schema.sql`

2\. `002\_create\_customers.sql`

3\. `003\_create\_accounts.sql`

4\. `004\_create\_merchants.sql`

5\. `005\_create\_transactions.sql`

6\. `006\_primary\_keys.sql`

7\. `007\_foreign\_keys.sql`

8\. `008\_foreign\_key\_indexes.sql`

9\. `009\_seed\_small\_data.sql`



\## How data gets into each database



| Database | Population method |

|---|---|

| `modernize` | One-time generated dataset, already loaded |

| `modernize\_buildtest` | Rebuilt from `sql/` scripts every time `reset-buildtest.ps1` runs |



\## Docker Compose



Both databases are hosted by the same PostgreSQL 18 container

defined in `docker-compose.yml`.

Start with `docker compose up -d` from the repo root.



\## Future direction



As the project grows, scripts may accept a database name parameter

so that schema operations can target either database explicitly.

A full dev/test/staging/prod separation is not planned at this stage.

