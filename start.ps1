[CmdletBinding()]
param()

# ==============================================================
# 新增模块：检查并自动获取管理员权限
# ==============================================================
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Warning "当前权限不足。正在请求管理员权限..."
    # 携带当前脚本路径，以管理员身份重新启动 PowerShell
    Start-Process powershell.exe -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit # 退出当前的普通权限窗口
}
# ==============================================================

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$runningApp = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -match 'com\.sojourners\.chess\.Main' }

if ($runningApp) {
    Write-Host 'Stopping the running Xiangqi application...'
    $runningApp | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
}

Write-Host 'Starting Xiangqi...'
$exitCode = 0
try {
    & mvn -B -o -DskipTests compile javafx:run
    $exitCode = $LASTEXITCODE
} finally {
    # Windows 上 javafx:run 可能留下独立的 JavaFX 子进程。无论正常退出还是 Ctrl+C，
    # 都按主类命令行清理残留进程，避免 Maven 已结束但界面仍存活。
    $remainingApp = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -match 'com\.sojourners\.chess\.Main' }
    if ($remainingApp) {
        Write-Host 'Stopping Xiangqi application...'
        $remainingApp | ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
    }
}
exit $exitCode
