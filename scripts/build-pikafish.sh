#!/usr/bin/env bash
# =============================================================================
# Pikafish 安卓引擎编译脚本（ANDROID_PLAN.md M2 / §5.1）
#
# 用法：
#   1) 准备 Pikafish 源码（官方 GPL 仓库）：
#        git clone --depth 1 https://github.com/official-pikafish/Pikafish.git
#   2) 准备 NDK（r27+），设置环境变量：
#        export ANDROID_NDK_HOME=/path/to/android-ndk-r27
#   3) 执行本脚本（在仓库根目录）：
#        ./scripts/build-pikafish.sh /path/to/Pikafish
#
# 脚本会为 arm64-v8a 与 x86_64 两个 ABI 编译并 strip，
# 统一改名为 libpikafish.so 放入 android-app/src/main/jniLibs/<abi>/。
# （伪装成 SO 是 targetSdk>=29 下唯一允许 exec 的方式，见计划 §5.1。）
# =============================================================================
set -euo pipefail

PIKAFISH_SRC="${1:?用法: build-pikafish.sh <Pikafish源码目录>}"
: "${ANDROID_NDK_HOME:?请先 export ANDROID_NDK_HOME=/path/to/android-ndk-r27}"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNILIBS="$REPO_ROOT/android-app/src/main/jniLibs"
MAKEFILE="$PIKAFISH_SRC/Makefile"

if [ ! -f "$MAKEFILE" ]; then
    echo "错误：$MAKEFILE 不存在" >&2
    exit 1
fi

NDKBUILD="$ANDROID_NDK_HOME/ndk-build"
API=24            # 与 minSdk 一致

mkdir -p "$JNILIBS/arm64-v8a" "$JNILIBS/x86_64"

# --- arm64-v8a（真机主力） ---------------------------------------------------
( cd "$PIKAFISH_SRC" && \
  make -j"$(nproc)" build ARCH=x86-64-android COMP=clang \
       CXX="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++" \
       CXXFLAGS="-target aarch64-linux-android$API -march=armv8-a -O3" \
       LDFLAGS="-target aarch64-linux-android$API -static-libstdc++" \
       EXE=pikafish-arm64 )
cp "$PIKAFISH_SRC/pikafish-arm64" "$JNILIBS/arm64-v8a/libpikafish.so"

# --- x86_64（模拟器调试） -----------------------------------------------------
( cd "$PIKAFISH_SRC" && \
  make -j"$(nproc)" build ARCH=x86-64-android COMP=clang \
       CXX="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++" \
       CXXFLAGS="-target x86_64-linux-android$API -O3" \
       LDFLAGS="-target x86_64-linux-android$API -static-libstdc++" \
       EXE=pikafish-x86_64 )
cp "$PIKAFISH_SRC/pikafish-x86_64" "$JNILIBS/x86_64/libpikafish.so"

# --- strip -------------------------------------------------------------------
STRIP="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
"$STRIP" "$JNILIBS/arm64-v8a/libpikafish.so"
"$STRIP" "$JNILIBS/x86_64/libpikafish.so"

ls -lh "$JNILIBS"/*/
echo "完成。libpikafish.so 已放入 android-app/src/main/jniLibs/{arm64-v8a,x86_64}/"
