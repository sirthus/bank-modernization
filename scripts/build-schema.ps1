docker cp .\sql\001_create_schema.sql pg18:/tmp/001_create_schema.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/001_create_schema.sql

docker cp .\sql\002_create_customers.sql pg18:/tmp/002_create_customers.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/002_create_customers.sql

docker cp .\sql\003_create_accounts.sql pg18:/tmp/003_create_accounts.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/003_create_accounts.sql

docker cp .\sql\004_create_merchants.sql pg18:/tmp/004_create_merchants.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/004_create_merchants.sql

docker cp .\sql\005_create_transactions.sql pg18:/tmp/005_create_transactions.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/005_create_transactions.sql

docker cp .\sql\006_primary_keys.sql pg18:/tmp/006_primary_keys.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/006_primary_keys.sql

docker cp .\sql\007_foreign_keys.sql pg18:/tmp/007_foreign_keys.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/007_foreign_keys.sql

docker cp .\sql\008_foreign_key_indexes.sql pg18:/tmp/008_foreign_key_indexes.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/008_foreign_key_indexes.sql

docker cp .\sql\009_seed_small_data.sql pg18:/tmp/009_seed_small_data.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/009_seed_small_data.sql

docker cp .\sql\010_create_batch_jobs.sql pg18:/tmp/010_create_batch_jobs.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/010_create_batch_jobs.sql

docker cp .\sql\011_create_transaction_batches.sql pg18:/tmp/011_create_transaction_batches.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/011_create_transaction_batches.sql

docker cp .\sql\012_create_batch_job_errors.sql pg18:/tmp/012_create_batch_job_errors.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/012_create_batch_job_errors.sql

docker cp .\sql\013_create_staged_transactions.sql pg18:/tmp/013_create_staged_transactions.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/013_create_staged_transactions.sql

docker cp .\sql\014_load_sample_batch.sql pg18:/tmp/014_load_sample_batch.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/014_load_sample_batch.sql

docker cp .\sql\016_load_bad_sample_batch.sql pg18:/tmp/016_load_bad_sample_batch.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/016_load_bad_sample_batch.sql

docker cp .\sql\015_validate_staged_transactions.sql pg18:/tmp/015_validate_staged_transactions.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/015_validate_staged_transactions.sql

docker cp .\sql\017_post_validated_transactions.sql pg18:/tmp/017_post_validated_transactions.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/017_post_validated_transactions.sql

docker cp .\sql\018_create_batch_reconciliations.sql pg18:/tmp/018_create_batch_reconciliations.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/018_create_batch_reconciliations.sql

docker cp .\sql\019_reconcile_batches.sql pg18:/tmp/019_reconcile_batches.sql
docker exec pg18 psql -U postgres -d modernize_buildtest -f /tmp/019_reconcile_batches.sql