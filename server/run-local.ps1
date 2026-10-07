param([string]$EnvFile = '.env')
$ErrorActionPreference = 'Stop'
# Resolve relative MEDIA_ROOT consistently, including when called from the repo root.
Push-Location $PSScriptRoot
try {
    foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($Matches[1], $value, 'Process')
        }
    }
    $javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    & $javaExecutable -jar 'build/libs/server-0.0.1-SNAPSHOT.jar' --spring.profiles.active=local
    if ($LASTEXITCODE -ne 0) { throw 'Spring local server exited with an error.' }
} finally { Pop-Location }
