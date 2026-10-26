# Build and install everything for the Philips 70PUH8215/96 (TPM191E) from this PC: Pure TV with this
# TV's dock and Live TV settings, and optionally the AirPlay receiver. Safe to run again after a change
# (dock order, wallpaper, a launcher update); nothing is disabled a second time.
#
# Usage (USB debugging on the TV must be on):
#   powershell -ExecutionPolicy Bypass -File examples\philips-tpm191e\install.ps1 -TvHost 192.168.1.116 [-AirPlay]
#
# Needs JDK 17 and the Android SDK (platform 34, build-tools 34), as set up on 26 Oct 2026 in
# %LOCALAPPDATA%\Android\Sdk.
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$AirPlay,
    [string]$AirPlayTag = 'v0.0.36',
    [string]$AirPlayName = 'Philips TV'
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$tv = Join-Path $repo 'tools\tv.py'

# 1. Pure TV: build, install, make it Home (the script disables the stock launcher the first time).
& (Join-Path $repo 'launcher\build-and-install.ps1') -TvHost $TvHost
# 2. This TV's dock, hidden apps and Live TV app (themes\philips-70puh8215\config.json).
& (Join-Path $repo 'tools\theme.ps1') -TvHost $TvHost (Join-Path $repo 'themes\philips-70puh8215')

# 3. Optional: the AirPlay receiver (jqssun/android-airplay-server, GPL-3, a separate app). Building it
#    needs a Linux or macOS host (FFmpeg's configure script), so on Windows the author's signed release
#    APK is installed instead; it includes this TV's armeabi-v7a libraries. Settings as in tv-vibehack's
#    airplay/build-and-install.sh.
if ($AirPlay) {
    $pkg = 'io.github.jqssun.airplay'
    $apk = Join-Path ([IO.Path]::GetTempPath()) "airplay-$AirPlayTag.apk"
    Invoke-WebRequest "https://github.com/jqssun/android-airplay-server/releases/download/$AirPlayTag/app-release.apk" -OutFile $apk
    & $py -I $tv --push $apk /data/local/tmp/airplay.apk
    @(
        'pm install -r /data/local/tmp/airplay.apk',
        'rm -f /data/local/tmp/airplay.apk',
        "am broadcast -a $pkg.SET_SETTING -n $pkg/.SettingsReceiver --es key server_name --es value '$AirPlayName'",
        "am broadcast -a $pkg.SET_SETTING -n $pkg/.SettingsReceiver --es key boot_auto_start --es value true",
        "dumpsys deviceidle whitelist +$pkg",
        "cmd appops set $pkg RUN_IN_BACKGROUND allow",
        # "Display over other apps": without it Android 10+ blocks launch_on_connect, and the picture
        # only shows once the app is opened by hand.
        "appops set $pkg SYSTEM_ALERT_WINDOW allow",
        # Launching once takes the app out of the "stopped" state, so BOOT_COMPLETED reaches it.
        "am start -n $pkg/.MainActivity",
        'input keyevent KEYCODE_HOME'
    ) | & $py -I $tv --batch
    Write-Host "AirPlay: the TV shows up as '$AirPlayName' under Screen Mirroring on iPhone/iPad/Mac."
}
Write-Host 'Done. Switch USB debugging off again on the TV.'
