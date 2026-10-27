param([int]$Port = 18083)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $project 'target/backend-0.0.1-SNAPSHOT.jar'
$secretFile = Join-Path $project 'config/test-secrets.properties'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the backend jar first with mvn.cmd package -DskipTests.' }
if (-not (Test-Path -LiteralPath $secretFile)) { throw 'Set up the isolated test database and config/test-secrets.properties first.' }
$line = Get-Content -LiteralPath $secretFile | Where-Object { $_ -match '^tripshield\.test\.db\.password=' } | Select-Object -First 1
if (-not $line) { throw 'The isolated test database password is missing.' }

$env:SPRING_PROFILES_ACTIVE = 'test'
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/tripshield_test'
$env:SPRING_DATASOURCE_USERNAME = 'tripshield_test_app'
$env:SPRING_DATASOURCE_PASSWORD = $line.Split('=', 2)[1]
$env:COORDINATOR_EMAIL = 'coordinator@tripshield.test'
$env:COORDINATOR_PASSWORD = 'integration-test-coordinator-password'

Push-Location $project
try {
    & java -jar $jar --server.port=$Port --spring.jpa.hibernate.ddl-auto=validate --tripshield.demo.flights=true --tripshield.outbox.enabled=false
} finally {
    Pop-Location
}
