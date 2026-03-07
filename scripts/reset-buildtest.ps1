docker exec pg18 psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS modernize_buildtest;"
docker exec pg18 psql -U postgres -d postgres -c "CREATE DATABASE modernize_buildtest;"

.\scripts\build-schema.ps1
.\scripts\verify-buildtest.ps1