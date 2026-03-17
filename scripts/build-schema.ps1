param(
    [Parameter(Mandatory=$true)]
    [string]$Database
)

$files = @(
    "001_create_schema.sql",
    "002_create_customers.sql",
    "003_create_accounts.sql",
    "004_create_merchants.sql",
    "005_create_transactions.sql",
    "006_create_indexes.sql",
    "007_create_batch_jobs.sql",
    "008_create_transaction_batches.sql",
    "009_create_batch_job_errors.sql",
    "010_create_staged_transactions.sql",
    "011_create_batch_reconciliations.sql",
    "013_drop_staged_account_fk.sql",
    "014_add_batch_id_to_transactions.sql"
)

foreach ($file in $files) {
    docker cp ".\sql\$file" "pg18:/tmp/$file" 2>$null
    docker exec pg18 psql -q -U postgres -d $Database -f "/tmp/$file"
}

# Seed data - prod gets the prod dataset, buildtest gets the small controlled
# dataset, everything else gets the standard large dataset
if ($Database -eq "modernize_prod") {
    $seedFile = "012_seed_data_prod.sql"
} elseif ($Database -eq "modernize_buildtest") {
    $seedFile = "012b_seed_small_data.sql"
} else {
    $seedFile = "012_seed_data.sql"
}

Write-Host "Loading seed data ($seedFile)..."
docker cp ".\sql\$seedFile" "pg18:/tmp/$seedFile" 2>$null
docker exec pg18 psql -q -U postgres -d $Database -f "/tmp/$seedFile"
Write-Host "Seed data loaded."
