param(
    [Parameter(Mandatory=$true)]
    [string]$Database
)

# Safety check for prod
if ($Database -eq "modernize_prod") {
    $confirm = Read-Host "You are about to WIPE modernize_prod. Type 'yes' to confirm"
    if ($confirm -ne "yes") {
        Write-Host "Aborted."
        return
    }
}

Write-Host "Resetting $Database..."

docker exec pg18 psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS $Database;"
docker exec pg18 psql -U postgres -d postgres -c "CREATE DATABASE $Database;"

.\scripts\build-schema.ps1 -Database $Database
.\scripts\verify-env.ps1 -Database $Database
