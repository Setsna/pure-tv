# Pure TV one-command installer for Windows (PowerShell).
#   irm https://raw.githubusercontent.com/Vardxn/pure-tv/main/install.ps1 | iex
#   or, from a clone:  .\install.ps1 <tv-ip>        undo:  .\install.ps1 rollback <tv-ip>
# Sets up a private Python environment in %USERPROFILE%\.puretv, then runs tools\puretv.py.
$ErrorActionPreference = "Stop"
$Repo = "Vardxn/pure-tv"
$HomeDir = if ($env:PURETV_HOME) { $env:PURETV_HOME } else { Join-Path $env:USERPROFILE ".puretv" }

$py = Get-Command python -ErrorAction SilentlyContinue
if (-not $py) { $py = Get-Command py -ErrorAction SilentlyContinue }
if (-not $py) {
    Write-Host "Pure TV needs Python 3: install it from https://www.python.org/downloads/ (tick 'Add python.exe to PATH')."
    exit 1
}

$here = if ($PSScriptRoot) { $PSScriptRoot } else { "" }
if ($here -and (Test-Path (Join-Path $here "tools\puretv.py"))) {
    $Src = $here
} else {
    $Src = Join-Path $HomeDir "src"
    if (Test-Path $Src) { Remove-Item -Recurse -Force $Src }
    New-Item -ItemType Directory -Force -Path $HomeDir | Out-Null
    Write-Host "Downloading Pure TV tools..."
    $zip = Join-Path $HomeDir "src.zip"
    Invoke-WebRequest "https://github.com/$Repo/archive/refs/heads/main.zip" -OutFile $zip
    Expand-Archive $zip -DestinationPath $HomeDir -Force
    Move-Item (Get-ChildItem $HomeDir -Directory | Where-Object Name -like "pure-tv-*" | Select-Object -First 1).FullName $Src
    Remove-Item $zip
}

$venvPy = Join-Path $HomeDir "venv\Scripts\python.exe"
if (-not (Test-Path $venvPy)) {
    Write-Host "Setting up a private Python environment in $HomeDir (one time)..."
    & $py.Source -m venv (Join-Path $HomeDir "venv")
}
& $venvPy -m pip install -q --disable-pip-version-check -r (Join-Path $Src "tools\requirements.txt")

$argv = @($args)
if ($argv.Count -eq 0) {
    $ip = Read-Host "Your TV's IP address (Settings > Network & Internet on the TV)"
    $argv = @("install", $ip)
} elseif ($argv[0] -notin @("install", "rollback", "probe")) {
    $argv = @("install") + $argv
}
& $venvPy -I (Join-Path $Src "tools\puretv.py") @argv
