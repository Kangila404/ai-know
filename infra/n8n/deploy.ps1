param(
    [ValidateSet('check', 'apply')][string]$Action = 'check',
    [string]$Workflow = 'ai-know-news-draft.json',
    [switch]$Replace,
    [string]$EnvFile,
    [string]$ComposeFile
)
$ErrorActionPreference = 'Stop'
$infraDirectory = Split-Path -Parent $PSScriptRoot
if (!$EnvFile) { $EnvFile = Join-Path $infraDirectory '.env' }
if (!$ComposeFile) { $ComposeFile = Join-Path $infraDirectory 'docker-compose.yml' }
$composeArguments = @('compose', '--env-file', $EnvFile, '-f', $ComposeFile)
$toolArguments = @($Action, $Workflow)
if ($Replace) { $toolArguments += '--replace' }
$restartN8n = $false
$deploymentExit = 0
$previousToken = $env:N8N_INGEST_TOKEN
try {
    if ($Action -eq 'apply') {
        $services = & docker @composeArguments config --services
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Compose services.' }
        if ($services -contains 'server') {
            $token = & docker @composeArguments exec -T server printenv N8N_INGEST_TOKEN
            if ($LASTEXITCODE -ne 0 -or !$token) {
                throw 'Cannot read ingestion token. Start/recreate the server with its updated env file first.'
            }
            $env:N8N_INGEST_TOKEN = ($token -join "`n").TrimEnd("`r", "`n")
        }
        $running = & docker @composeArguments ps --status running -q n8n
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect n8n status.' }
        if ($running) {
            $restartN8n = $true
            & docker @composeArguments stop -t 60 n8n
            if ($LASTEXITCODE -ne 0) { throw 'Cannot stop n8n before import.' }
        }
    }
    $credentialArguments = @()
    if ($env:N8N_INGEST_TOKEN) { $credentialArguments = @('-e', 'N8N_INGEST_TOKEN') }
    & docker @composeArguments run --rm --no-deps @credentialArguments n8n-workflows @toolArguments
    $deploymentExit = $LASTEXITCODE
} finally {
    $env:N8N_INGEST_TOKEN = $previousToken
    if ($restartN8n) {
        & docker @composeArguments start n8n
        if ($LASTEXITCODE -ne 0) { throw 'Import finished but n8n restart failed; start the n8n service manually.' }
    }
}
if ($deploymentExit -ne 0) { throw 'Workflow deployment failed. See the message above.' }
