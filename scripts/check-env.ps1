# 环境自检：只读取版本号，不修改任何文件。
# 用法：powershell -ExecutionPolicy Bypass -File scripts\check-env.ps1

$ErrorActionPreference = "Continue"
$results = @()

function Add-Result {
    param([string]$Name, [string]$Command, [string]$Detail, [string]$Status)
    $script:results += [pscustomobject]@{
        Item   = $Name
        Status = $Status
        Detail = $Detail
        Command = $Command
    }
}

function Test-Tool {
    param([string]$Name, [string]$Command, [string]$ArgList, [string]$Hint)
    try {
        $output = & $Command $ArgList 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) { throw ($output.Trim()) }
        $firstLine = ($output.Trim() -split "`r?`n")[0]
        Add-Result -Name $Name -Command "$Command $ArgList" -Detail $firstLine -Status "PASS"
    }
    catch {
        Add-Result -Name $Name -Command "$Command $ArgList" -Detail $Hint -Status "FAIL"
    }
}

Write-Host "Campus Lost & Found - environment check" -ForegroundColor Cyan
Write-Host ("Working directory: " + (Get-Location)) -ForegroundColor DarkGray
Write-Host ""

Test-Tool -Name "Node.js" -Command "node" -ArgList "-v" -Hint "Install Node.js 20 LTS or newer"
Test-Tool -Name "pnpm" -Command "pnpm" -ArgList "-v" -Hint "Run: corepack enable"
Test-Tool -Name "Git" -Command "git" -ArgList "--version" -Hint "Install Git for Windows"

$javaRaw = ""
try {
    $javaRaw = (& java -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "java not found" }
    $javaLine = ($javaRaw.Trim() -split "`r?`n")[0]
    Add-Result -Name "JDK" -Command "java -version" -Detail $javaLine -Status "PASS"
    if ($javaLine -notmatch '"2[1-9]\.' -and $javaLine -notmatch '"1[89]\.' -and $javaLine -notmatch '"17\.') {
        Add-Result -Name "JDK version" -Command "java -version" -Detail "Project targets Java 21; current runtime may not match" -Status "WARN"
    }
}
catch {
    Add-Result -Name "JDK" -Command "java -version" -Detail "Install JDK 21 and set JAVA_HOME" -Status "FAIL"
}

Test-Tool -Name "Maven" -Command "mvn" -ArgList "-v" -Hint "Maven failed to start: check conf\settings.xml for XML errors, or use the Maven bundled with IntelliJ IDEA"

$pythonFound = $false
foreach ($ver in @("3.13", "3.12", "3.11")) {
    try {
        $pyOut = (& py "-$ver" --version 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0) {
            Add-Result -Name "Python" -Command "py -$ver --version" -Detail $pyOut.Trim() -Status "PASS"
            $pythonFound = $true
            break
        }
    }
    catch { }
}
if (-not $pythonFound) {
    Add-Result -Name "Python" -Command "py -3.13 --version" -Detail "Install Python 3.11+; do not use the 3.8 runtime" -Status "FAIL"
}

Test-Tool -Name "Docker CLI" -Command "docker" -ArgList "--version" -Hint "Install Docker Desktop"

try {
    $serverVersion = (& docker info --format "{{.ServerVersion}}" 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($serverVersion)) { throw "daemon not running" }
    Add-Result -Name "Docker daemon" -Command "docker info" -Detail ("server " + $serverVersion) -Status "PASS"
}
catch {
    Add-Result -Name "Docker daemon" -Command "docker info" -Detail "Start Docker Desktop and wait for Running state" -Status "WARN"
}

try {
    $composeOut = (& docker compose version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "compose plugin missing" }
    $composeLine = ($composeOut.Trim() -split "`r?`n")[0]
    Add-Result -Name "Compose" -Command "docker compose version" -Detail $composeLine -Status "PASS"
}
catch {
    Add-Result -Name "Compose" -Command "docker compose version" -Detail "Update Docker Desktop to get the compose plugin" -Status "WARN"
}

$results | Format-Table -AutoSize -Wrap

$failed = @($results | Where-Object { $_.Status -eq "FAIL" }).Count
$warned = @($results | Where-Object { $_.Status -eq "WARN" }).Count

Write-Host ""
if ($failed -gt 0) {
    Write-Host ("Result: " + $failed + " blocking issue(s), " + $warned + " warning(s).") -ForegroundColor Red
    exit 1
}

Write-Host ("Result: ready. " + $warned + " warning(s).") -ForegroundColor Green
exit 0

