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