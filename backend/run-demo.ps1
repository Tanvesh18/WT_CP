param([int]$Port = 18084)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $project 'target/backend-0.0.1-SNAPSHOT.jar'
$secretsPath = Join-Path $project 'config/demo-secrets.properties'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the backend jar first with mvn.cmd package -DskipTests.' }
if (-not (Test-Path -LiteralPath $secretsPath)) { throw 'Run setup-demo.ps1 first.' }
$secrets = @{}
Get-Content -LiteralPath $secretsPath | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { $secrets[$matches[1].Trim()] = $matches[2].Trim() } }
foreach ($key in @('demo.db.password', 'demo.coordinator.password')) { if (-not $secrets[$key]) { throw "Missing $key" } }
$env:SPRING_PROFILES_ACTIVE = 'demo'
$env:DEMO_DB_PASSWORD = $secrets['demo.db.password']
$env:COORDINATOR_EMAIL = 'coordinator@demo.tripshield.test'
$env:COORDINATOR_PASSWORD = $secrets['demo.coordinator.password']
Push-Location $project
try { & java -jar $jar --server.port=$Port }
finally { Pop-Location }
