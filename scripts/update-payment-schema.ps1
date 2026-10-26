[CmdletBinding()]
param(
    [ValidateSet('Check','DryRun','Apply')][string]$Mode = 'Check',
    [string]$JdbcJar,
    [string]$ConfigPath
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $ConfigPath) { $ConfigPath = Join-Path $projectRoot 'config/config.local.json' }
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$dbPassword = $env:DB_PASSWORD
if (-not $dbPassword -and (Test-Path (Join-Path $projectRoot '.env'))) {
    foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env')) {
        if ($line -match '^\s*DB_PASSWORD\s*=\s*(.*)\s*$') { $dbPassword = $matches[1] }
    }
}
if (-not $dbPassword) { throw 'Set DB_PASSWORD or provide it in .env.' }
if (-not $JdbcJar) {
    $searchRoots = @((Join-Path $env:USERPROFILE '.m2/repository/org/postgresql/postgresql'),
        (Join-Path $projectRoot '.runtime/completion-changes/maven-repository/org/postgresql/postgresql'))
    $JdbcJar = Get-ChildItem -LiteralPath $searchRoots -Recurse -Filter 'postgresql-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '(sources|javadoc)' } |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $JdbcJar -or -not (Test-Path -LiteralPath $JdbcJar)) { throw 'Provide -JdbcJar with the PostgreSQL JDBC driver path (or build the backend first).' }
$oldUrl=$env:PAYMENT_DB_URL; $oldUser=$env:PAYMENT_DB_USER; $oldPassword=$env:PAYMENT_DB_PASSWORD
try {
    $env:PAYMENT_DB_URL = 'jdbc:postgresql://{0}:{1}/{2}' -f $config.database.host,$config.database.port,$config.database.database
    $env:PAYMENT_DB_USER = [string]$config.database.username
    $env:PAYMENT_DB_PASSWORD = $dbPassword
    $sqlPath = Join-Path $projectRoot 'backendqt/backendqt/src/main/resources/db/migration/20261024_01_allow_multiple_invoice_payments.sql'
    & java --class-path $JdbcJar (Join-Path $PSScriptRoot 'PaymentSchemaUpgrade.java') $Mode $sqlPath
    if ($LASTEXITCODE -ne 0) { throw "Payment schema $Mode failed; see error above." }
} finally {
    $env:PAYMENT_DB_URL=$oldUrl; $env:PAYMENT_DB_USER=$oldUser; $env:PAYMENT_DB_PASSWORD=$oldPassword
    $dbPassword=$null
}
