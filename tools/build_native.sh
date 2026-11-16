#!/usr/bin/env bash
#
# 原生版（Kotlin + Compose）构建脚本。
#
# 环境（均在本机 D 盘，无需重新安装）：
#   JDK 21       D:\jdk21
#   Android SDK  D:\android-sdk   （platform 36 / build-tools 36.0.0）
#   Gradle       8.14.3（wrapper 已缓存）
#
# 用法：
#   bash tools/build_native.sh            # debug
#   bash tools/build_native.sh release    # release（需先配置签名）
#
# 注意：工程位于中文路径下，依赖 gradle.properties 里的 android.overridePathCheck=true。
#
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-D:\\jdk21}"
export ANDROID_HOME="${ANDROID_HOME:-D:\\android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-D:\\android-sdk}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE="$ROOT/native"
VARIANT="${1:-debug}"

cd "$NATIVE"

if [ "$VARIANT" = "release" ]; then
  ./gradlew assembleRelease --no-daemon --console=plain
  OUT="$NATIVE/app/build/outputs/apk/release/app-release-unsigned.apk"
  TARGET="$ROOT/build/DeepSeek-Native-release-unsigned.apk"
else
  ./gradlew assembleDebug --no-daemon --console=plain
  OUT="$NATIVE/app/build/outputs/apk/debug/app-debug.apk"
  TARGET="$ROOT/build/DeepSeek-Native-debug.apk"
fi

mkdir -p "$ROOT/build"
cp "$OUT" "$TARGET"

echo
echo "完成：$TARGET"
ls -lh "$TARGET"
