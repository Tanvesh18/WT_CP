param([string]$ApiUrl = 'http://127.0.0.1:18084')

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$secretsPath = Join-Path $project 'config/demo-secrets.properties'
if ($ApiUrl -notmatch '^http://(127\.0\.0\.1|localhost):\d+$') { throw 'Only a local demo endpoint is allowed.' }
if (-not (Test-Path -LiteralPath $mysql) -or -not (Test-Path -LiteralPath $secretsPath)) { throw 'Run setup-demo.ps1 before resetting the demo.' }
$identity = Invoke-RestMethod -Uri "$ApiUrl/api/demo/identity" -TimeoutSec 5
if ($identity.mode -ne 'DEMO' -or $identity.database -ne 'tripshield_demo') { throw 'Backend identity is not the isolated demo profile.' }
$values = @{}
Get-Content -LiteralPath $secretsPath | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { $values[$matches[1].Trim()] = $matches[2].Trim() } }
if (-not $values['demo.db.password']) { throw 'Demo database secret is missing.' }
$env:MYSQL_PWD = $values['demo.db.password']
try {
    $database = & $mysql --host=127.0.0.1 --port=3306 --user=tripshield_demo_app --database=tripshield_demo --batch --skip-column-names --execute 'SELECT DATABASE(),CURRENT_USER();'
    if ($LASTEXITCODE -ne 0 -or $database -ne "tripshield_demo`ttripshield_demo_app@127.0.0.1") { throw 'Database connection is not the restricted demo account and schema.' }
    $sql = 'START TRANSACTION; DELETE FROM notification_outbox; DELETE FROM user_notification; DELETE FROM trip_invitation; DELETE FROM trip_create_key; DELETE FROM trip_event; DELETE FROM alternative; DELETE FROM trip_item; DELETE FROM trip; DELETE FROM app_session; DELETE FROM app_user; COMMIT;'
    & $mysql --host=127.0.0.1 --port=3306 --user=tripshield_demo_app --database=tripshield_demo --batch --execute $sql
    if ($LASTEXITCODE -ne 0) { throw 'Demo-only reset failed.' }
} finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
$env:DEMO_API_URL = $ApiUrl
& node (Join-Path $project 'seed-demo.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Demo seed failed after reset.' }
