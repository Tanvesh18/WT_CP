param()

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$secretsPath = Join-Path $project 'config/demo-secrets.properties'
$localPath = Join-Path $project 'config/local.properties'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
if (-not (Test-Path -LiteralPath $mysql)) { throw 'MySQL client not found at the documented MySQL 8 installation path.' }
if (-not (Test-Path -LiteralPath $localPath)) { throw 'Local MySQL administrator configuration is needed to create the isolated demo schema.' }

function Read-Properties([string]$path) {
    $values = @{}
    Get-Content -LiteralPath $path | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') { $values[$matches[1].Trim()] = $matches[2].Trim() }
    }
    return $values
}

$admin = Read-Properties $localPath
if (-not $admin['spring.datasource.username'] -or -not $admin['spring.datasource.password']) { throw 'Local MySQL administrator credentials are incomplete.' }
$env:MYSQL_PWD = $admin['spring.datasource.password']
try {
    $existing = & $mysql --host=127.0.0.1 --port=3306 "--user=$($admin['spring.datasource.username'])" --batch --skip-column-names --execute "SHOW DATABASES LIKE 'tripshield_demo';"
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect MySQL schemas.' }
    if ($existing -and -not (Test-Path -LiteralPath $secretsPath)) { throw 'tripshield_demo already exists without this project demo secret file. Refusing to change an unknown schema.' }
    if (-not (Test-Path -LiteralPath $secretsPath)) {
        function New-Secret {
            $bytes = New-Object byte[] 20
            $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
            return -join ($bytes | ForEach-Object { $_.ToString('x2') })
        }
        @(
            "demo.db.password=$(New-Secret)"
            'demo.coordinator.email=coordinator@demo.tripshield.test'
            "demo.coordinator.password=$(New-Secret)"
            'demo.traveler.maya.email=maya@demo.tripshield.test'
            'demo.traveler.arjun.email=arjun@demo.tripshield.test'
            'demo.traveler.nisha.email=nisha@demo.tripshield.test'
            "demo.traveler.password=$(New-Secret)"
        ) | Set-Content -LiteralPath $secretsPath -Encoding utf8
    }
    $secrets = Read-Properties $secretsPath
    foreach ($key in @('demo.db.password', 'demo.coordinator.password', 'demo.traveler.password')) {
        if ($secrets[$key] -notmatch '^[a-f0-9]{40}$') { throw "Invalid $key in demo secret file." }
    }
    $sql = "CREATE DATABASE IF NOT EXISTS tripshield_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'tripshield_demo_app'@'127.0.0.1' IDENTIFIED BY '$($secrets['demo.db.password'])'; GRANT ALL PRIVILEGES ON tripshield_demo.* TO 'tripshield_demo_app'@'127.0.0.1';"
    $sql | & $mysql --host=127.0.0.1 --port=3306 "--user=$($admin['spring.datasource.username'])" --batch
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the isolated demo schema.' }
    Write-Output 'Isolated tripshield_demo schema and restricted account are ready. Secrets remain in ignored backend/config/demo-secrets.properties.'
} finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
