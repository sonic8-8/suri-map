param(
    [string]$Route = "p2-incident-list",
    [string]$Device = "emulator-5554",
    [switch]$SkipInstall,
    [string]$JavaHome
)

$ErrorActionPreference = "Stop"

$androidRoot = $PSScriptRoot
$repoRoot = Split-Path -Parent $androidRoot
Set-Location $androidRoot
$gradle = Join-Path $androidRoot "gradlew.bat"

if (-not $JavaHome) {
    $JavaHome = $env:JAVA_HOME
}

if (-not $JavaHome) {
    $defaultJavaHome = "C:\Users\SSAFY\.jdks\corretto-17.0.19"
    if (Test-Path $defaultJavaHome) {
        $JavaHome = $defaultJavaHome
    }
}

if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\bin;$env:PATH"
}

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "adb not found at $adb"
}

$validRoutes = @(
    "p1-auth-bootstrap",
    "p2-incident-list",
    "p3-offline-package",
    "p5-synced",
    "p5-search-map",
    "p5-blocked-outbox",
    "p6-handover-summary",
    "p6-handover-memo",
    "p8-incident-alert",
    "p9-marker-detail",
    "blocked-outbox"
)

if ($validRoutes -notcontains $Route) {
    $routes = $validRoutes -join ", "
    throw "Unknown QA route '$Route'. Available routes: $routes"
}

if (-not $SkipInstall) {
    Write-Host "Installing debug build..."
    & $gradle :app:installDebug
}

Write-Host "Opening QA route '$Route' on '$Device'..."
& $adb -s $Device shell am start -n com.surimap/.ui.qa.DeviceQaActivity --es qa_route $Route
