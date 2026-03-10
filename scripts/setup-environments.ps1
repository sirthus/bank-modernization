Write-Host ""
Write-Host "=== Setting up all environments ==="
Write-Host ""

# Drop old database
Write-Host "Dropping old modernize database..."
docker exec pg18 psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS modernize;"

# Create all four databases
$databases = @("modernize_prod", "modernize_test", "modernize_dev", "modernize_buildtest")

foreach ($db in $databases) {
    Write-Host ""
    Write-Host "--- Setting up $db ---"
    docker exec pg18 psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS $db;"
    docker exec pg18 psql -U postgres -d postgres -c "CREATE DATABASE $db;"
    .\scripts\build-schema.ps1 -Database $db
}

Write-Host ""
Write-Host "=== Verifying all environments ==="
Write-Host ""

foreach ($db in $databases) {
    docker exec pg18 psql -U postgres -d $db -c "
    select '$db' as database,
           (select count(*) from bank.customers) as customers,
           (select count(*) from bank.accounts) as accounts,
           (select count(*) from bank.merchants) as merchants;
    "
}

Write-Host ""
Write-Host "=== Setup complete ==="
Write-Host ""
