# Restores the two engine components changed on 2026-11-22.
# Default is preview only; -Apply explicitly restores their original DEFAULT state.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$AdbPath,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
if (-not $AdbPath) {
    if ($env:ANDROID_HOME) { $AdbPath = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' }
    elseif ($env:LOCALAPPDATA) { $AdbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
    else { throw 'Specify -AdbPath.' }
}
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'ADB executable not found.' }
$state = & $AdbPath -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or "$state".Trim() -ne 'device') { throw 'Selected phone is unavailable.' }
$hash = & $AdbPath -s $Serial shell sha256sum /product/priv-app/SecurityCenter/SecurityCenter.apk
if ($LASTEXITCODE -ne 0 -or "$hash" -notmatch '^bdf752602a0fdf8dd91be700164f913e8cdf2ed50b8a2f7475f477b67022516f\s') {
    throw 'SecurityCenter version changed. Review component names before restoring.'
}
$components = @(
    'com.miui.guardprovider/com.miui.guardprovider.server.VirusScanService',
    'com.miui.guardprovider/com.miui.guardprovider.manager.SecurityService'
)
foreach ($component in $components) {
    $command = "pm default-state --user 0 $component"
    if (-not $Apply) { Write-Output "PREVIEW: $command"; continue }
    $result = & $AdbPath -s $Serial shell "su -c '$command'"
    Write-Output $result
    if ($LASTEXITCODE -ne 0 -or "$result" -notmatch 'new state: default') {
        throw "Restore failed: $component"
    }
}
if ($Apply) { Write-Output 'Original engine component states restored. Restart and verify SecurityCenter scheduling; prior pending-job deadlines are not recreated by this script.' }
else { Write-Output 'Preview only. Use -Apply to restore the original engine states.' }
