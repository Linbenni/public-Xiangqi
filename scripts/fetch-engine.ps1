# 一键下载并安装安卓版 Pikafish 引擎（Windows / PowerShell）
# 用法：  pwsh -File scripts/fetch-engine.ps1
# 产物：
#   android-app/src/main/jniLibs/arm64-v8a/libpikafish.so   （真机 arm64）
#   android-app/src/main/assets/pikafish.nnue               （NNUE 权重，必配）
# 缓存：engine/Pikafish-<tag>/（官方 release 原始内容）

$ErrorActionPreference = 'Stop'

$repo = 'official-pikafish/Pikafish'
$ua = 'tchess-fetch-engine'
$root = Split-Path -Parent $PSScriptRoot

# ---- 1. 取最新 release tag ----
Write-Host '[1/4] 查询最新 Pikafish release ...'
$rel = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/latest" -Headers @{ 'User-Agent' = $ua }
$tag = $rel.tag_name
$asset = $rel.assets | Where-Object { $_.name -match '\.7z$' } | Select-Object -First 1
if (-not $asset) { throw "release $tag 中未找到 7z 资产" }
Write-Host "      tag=$tag asset=$($asset.name)"

# ---- 2. 下载（已存在则跳过）----
$cacheDir = Join-Path $root "engine\Pikafish-$tag"
$archive = Join-Path $root "engine\$($asset.name)"
New-Item -ItemType Directory -Force -Path (Join-Path $root 'engine') | Out-Null
if ((Test-Path $cacheDir) -and (Test-Path "$cacheDir\Android\pikafish-armv8")) {
    Write-Host '[2/4] 已有缓存，跳过下载'
} else {
    if (-not (Test-Path $archive)) {
        Write-Host "[2/4] 下载 $($asset.name) ..."
        Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archive -UserAgent $ua
    }
    # ---- 3. 解压（7zr.exe 官方独立解压器，无需安装）----
    Write-Host '[3/4] 解压 ...'
    $szr = Join-Path $env:TEMP '7zr.exe'
    if (-not (Test-Path $szr)) {
        Invoke-WebRequest -Uri 'https://www.7-zip.org/a/7zr.exe' -OutFile $szr -UserAgent $ua
    }
    & $szr x -y "-o$cacheDir" $archive | Out-Null
}

$arm = Join-Path $cacheDir 'Android\pikafish-armv8'
$nnue = Join-Path $cacheDir 'pikafish.nnue'
foreach ($f in @($arm, $nnue)) {
    if (-not (Test-Path $f)) { throw "缓存中缺少 $f" }
}

# ---- 4. 放置到 android-app ----
Write-Host '[4/4] 安装到 android-app ...'
$jniDir = Join-Path $root 'android-app\src\main\jniLibs\arm64-v8a'
New-Item -ItemType Directory -Force -Path $jniDir | Out-Null
Copy-Item $arm (Join-Path $jniDir 'libpikafish.so') -Force
Copy-Item $nnue (Join-Path $root 'android-app\src\main\assets\pikafish.nnue') -Force

Write-Host ''
Write-Host '完成：'
Get-Item (Join-Path $jniDir 'libpikafish.so'), (Join-Path $root 'android-app\src\main\assets\pikafish.nnue') |
    ForEach-Object { Write-Host ("  {0}  ({1:N0} bytes)" -f $_.FullName.Substring($root.Length + 1), $_.Length) }
Write-Host ''
Write-Host '说明：x86_64 模拟器版本官方未发布，需要时用 scripts/build-pikafish.sh + NDK 自编。'
