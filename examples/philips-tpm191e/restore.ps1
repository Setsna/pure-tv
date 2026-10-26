# Undo everything done to the Philips 70PUH8215/96 (TPM191E): re-enables every package in
# debloat.txt, makes the stock Android TV home the default again, turns AdGuard Private DNS off and,
# with -Uninstall, removes Pure TV, SmartTube and the AirPlay receiver.
#
# Usage (USB debugging on the TV must be switched on again first):
#   powershell -ExecutionPolicy Bypass -File examples\philips-tpm191e\restore.ps1 -TvHost 192.168.1.116 [-Uninstall]
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: py -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }

# Packages back on, stock launcher back as Home, animation scales reset.
& (Join-Path $repo 'tools\restore.ps1') -TvHost $TvHost (Join-Path $PSScriptRoot 'debloat.txt') -Stock com.google.android.tvlauncher

# Private DNS back to Android's default ("Automatic"); it was unset before.
@(
    'settings put global private_dns_mode opportunistic',
    'settings delete global private_dns_specifier'
) | & $py -I (Join-Path $repo 'tools\tv.py') --batch

if ($Uninstall) {
    @(
        'pm uninstall dev.vardxn.puretv',
        'pm uninstall org.smarttube.stable',
        'pm uninstall io.github.jqssun.airplay'
    ) | & $py -I (Join-Path $repo 'tools\tv.py') --batch
}
& $py -I (Join-Path $repo 'tools\tv.py') 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1'
Write-Host 'Restored. Switch USB debugging off again on the TV when done.'
