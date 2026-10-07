# Creates one labelled PENDING test submission in the local database.
param()
$ErrorActionPreference = 'Stop'
$infraDirectory = Split-Path -Parent $PSScriptRoot
$repoDirectory = Split-Path -Parent $infraDirectory
$serverEnv = Join-Path $repoDirectory 'server/.env'
$tokenLine = Get-Content $serverEnv -Encoding UTF8 | Where-Object { $_ -match '^N8N_INGEST_TOKEN=' } | Select-Object -Last 1
if (-not $tokenLine) { throw 'Configure N8N_INGEST_TOKEN in server/.env and restart Spring first.' }
$token = $tokenLine.Substring('N8N_INGEST_TOKEN='.Length).Trim().Trim('"').Trim("'")
if ($token.Length -lt 32) { throw 'N8N_INGEST_TOKEN must be at least 32 characters.' }
$composeArguments = @('compose', '--env-file', (Join-Path $infraDirectory '.env'), '-f', (Join-Path $infraDirectory 'docker-compose.yml'))
$restartN8n = $false
$result = 0
try {
    $running = & docker @composeArguments ps --status running -q n8n
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect n8n status.' }
    if ($running) {
        $restartN8n = $true
        & docker @composeArguments stop -t 60 n8n
        if ($LASTEXITCODE -ne 0) { throw 'Cannot stop n8n.' }
    }
    # The secret travels over stdin and is encrypted in n8n's credential store.
    @{ token = $token } | ConvertTo-Json -Compress | & docker @composeArguments run --rm --no-deps -T --entrypoint node n8n-workflows /opt/aiknow/verify-ingestion.mjs
    $result = $LASTEXITCODE
} finally {
    $token = $null
    if ($restartN8n) {
        & docker @composeArguments start n8n
        if ($LASTEXITCODE -ne 0) { throw 'Cannot restart n8n after verification.' }
    }
}
if ($result -ne 0) { throw 'Ingestion verification failed.' }
