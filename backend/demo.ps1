$ErrorActionPreference = 'Stop'
$backend = $PSScriptRoot
$job = $null
try {
    if (-not (Test-Path (Join-Path $backend 'config/demo-secrets.properties'))) {
        Write-Host 'Setting up the demo database...'
        & (Join-Path $backend 'setup-demo.ps1')
    }
    Write-Host 'Building the backend...'
    Push-Location $backend
    try {
        & mvn.cmd package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    } finally { Pop-Location }
    $listener = Get-NetTCPConnection -LocalPort 18084 -State Listen -ErrorAction SilentlyContinue
    if ($listener) { throw 'Port 18084 is already in use. Stop the existing server and try again.' }
    Write-Host 'Starting the demo backend...'
    $job = Start-Job -ScriptBlock { param($path) & $path -Port 18084 } -ArgumentList (Join-Path $backend 'run-demo.ps1')
    $ready = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if ($job.State -ne 'Running') { break }
        try {
            $identity = Invoke-RestMethod 'http://127.0.0.1:18084/api/demo/identity' -TimeoutSec 1
            if ($identity.mode -eq 'DEMO' -and $identity.database -eq 'tripshield_demo') { $ready = $true; break }
            throw 'Port 18084 is already serving a different application.'
        } catch {
            if ($_.Exception.Message -like 'Port 18084*') { throw }
            Start-Sleep -Seconds 1
        }
    }
    if (-not $ready) {
        Receive-Job $job -ErrorAction Continue
        throw 'Demo backend did not start. Check MySQL and whether port 18084 is available.'
    }
    Write-Host 'Preparing demo scenarios...'
    & node (Join-Path $backend 'seed-demo.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Demo seeding failed.' }
    Write-Host 'Demo backend is ready. Press Ctrl+C to stop.'
    Receive-Job $job -Wait
} finally {
    if ($job) { Stop-Job $job -ErrorAction SilentlyContinue; Remove-Job $job -Force -ErrorAction SilentlyContinue }
}
