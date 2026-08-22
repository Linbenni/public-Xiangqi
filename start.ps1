[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$runningApp = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -match 'com\.sojourners\.chess\.Main' }

if ($runningApp) {
    Write-Host 'Stopping the running Xiangqi application...'
    $runningApp | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
}

Write-Host 'Starting Xiangqi...'
& mvn -B -o -DskipTests compile javafx:run
exit $LASTEXITCODE
