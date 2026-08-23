#!/usr/bin/env bash
# 一键下载并安装安卓版 Pikafish 引擎（Linux / macOS，CI 与本地通用）
# 用法：  ./scripts/fetch-engine.sh
# 依赖：  curl、7z（p7zip-full）；缺失时尝试 pip 安装 py7zr 兜底解压。
# 产物：
#   android-app/src/main/jniLibs/arm64-v8a/libpikafish.so   （真机 arm64）
#   android-app/src/main/assets/pikafish.nnue               （NNUE 权重，必配）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="official-pikafish/Pikafish"

echo "[1/4] 查询最新 Pikafish release ..."
TAG=$(curl -fsSL -H 'User-Agent: tchess-fetch-engine' "https://api.github.com/repos/$REPO/releases/latest" | grep -o '"tag_name": *"[^"]*"' | head -1 | sed 's/.*"tag_name": *"//;s/"$//')
ASSET=$(curl -fsSL -H 'User-Agent: tchess-fetch-engine' "https://api.github.com/repos/$REPO/releases/tags/$TAG" | grep -o '"name": *"[^"]*\.7z"' | head -1 | sed 's/.*"name": *"//;s/"$//')
[ -n "$TAG" ] || { echo "未获取到 release tag"; exit 1; }
[ -n "$ASSET" ] || { echo "release $TAG 中未找到 7z 资产"; exit 1; }
echo "      tag=$TAG asset=$ASSET"

CACHE="$ROOT/engine/Pikafish-$TAG"
ARCHIVE="$ROOT/engine/$ASSET"
mkdir -p "$ROOT/engine"

if [ -f "$CACHE/Android/pikafish-armv8" ]; then
    echo "[2/4] 已有缓存，跳过下载"
else
    if [ ! -f "$ARCHIVE" ]; then
        echo "[2/4] 下载 $ASSET ..."
        curl -fL --retry 3 -H 'User-Agent: tchess-fetch-engine' \
            -o "$ARCHIVE" "https://github.com/$REPO/releases/download/$TAG/$ASSET"
    fi
    echo "[3/4] 解压 ..."
    if command -v 7z >/dev/null 2>&1; then
        7z x -y -o"$CACHE" "$ARCHIVE" >/dev/null
    else
        echo "      未找到 7z，使用 py7zr 解压 ..."
        python3 -m pip install --quiet py7zr
        python3 - "$ARCHIVE" "$CACHE" <<'PY'
import sys
from py7zr import SevenZipFile
SevenZipFile(sys.argv[1]).extractall(path=sys.argv[2])
PY
    fi
fi

ARM="$CACHE/Android/pikafish-armv8"
NNUE="$CACHE/pikafish.nnue"
[ -f "$ARM" ] || { echo "缓存中缺少 $ARM"; exit 1; }
[ -f "$NNUE" ] || { echo "缓存中缺少 $NNUE"; exit 1; }

echo "[4/4] 安装到 android-app ..."
mkdir -p "$ROOT/android-app/src/main/jniLibs/arm64-v8a"
cp -f "$ARM" "$ROOT/android-app/src/main/jniLibs/arm64-v8a/libpikafish.so"
cp -f "$NNUE" "$ROOT/android-app/src/main/assets/pikafish.nnue"

ls -l "$ROOT/android-app/src/main/jniLibs/arm64-v8a/libpikafish.so" \
      "$ROOT/android-app/src/main/assets/pikafish.nnue"
echo ""
echo "说明：x86_64 模拟器版本官方未发布，需要时用 scripts/build-pikafish.sh + NDK 自编。"
