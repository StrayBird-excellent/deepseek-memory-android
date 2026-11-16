#!/usr/bin/env bash
#
# 一条命令出 APK。
#
# 环境依赖（均在本机 D 盘，无需重新安装）：
#   JDK 21      D:\jdk21
#   Android SDK D:\android-sdk   （platform 36 / build-tools 36.0.0）
#
# 用法：
#   bash tools/build_apk.sh                    # debug 包，版本号读 package.json
#   bash tools/build_apk.sh release            # release 包（需要先配好签名）
#   APK_VERSION=2.2 bash tools/build_apk.sh    # 指定版本号
#
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-D:\\jdk21}"
export ANDROID_HOME="${ANDROID_HOME:-D:\\android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-D:\\android-sdk}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app"
VARIANT="${1:-debug}"

cd "$APP"

echo "==> 1/4 类型检查"
npx tsc -p tsconfig.json

echo "==> 2/4 构建前端"
NODE_ENV=production npx rspack build -c rspack.config.ts

echo "==> 3/4 同步到原生工程"
npx cap sync android

echo "==> 4/4 打包 $VARIANT"
cd "$APP/android"
if [ "$VARIANT" = "release" ]; then
  ./gradlew assembleRelease --no-daemon --console=plain
  OUT="$APP/android/app/build/outputs/apk/release/app-release.apk"
else
  ./gradlew assembleDebug --no-daemon --console=plain
  OUT="$APP/android/app/build/outputs/apk/debug/app-debug.apk"
fi

mkdir -p "$ROOT/build"
# 版本号：用 APK_VERSION 指定（如 APK_VERSION=2.2），默认 1.0
VERSION="${APK_VERSION:-1.0}"
if [ "$VARIANT" = "release" ]; then
  # 正式版：干净的文件名，便于提交审核
  TARGET="$ROOT/build/DeepSeek-Memory-v${VERSION}-release.apk"
else
  TARGET="$ROOT/build/DeepSeek-Memory-v${VERSION}-debug.apk"
fi
cp "$OUT" "$TARGET"

echo
echo "完成：$TARGET"
ls -lh "$TARGET"
