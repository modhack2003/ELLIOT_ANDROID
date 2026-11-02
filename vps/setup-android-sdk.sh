#!/usr/bin/env bash
# EILIOT build box setup — installs Android SDK on the Kali VPS.
# Idempotent: safe to re-run.
set -euo pipefail

JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

SDK="$HOME/android-sdk"
CLT_ZIP="/tmp/cmdline-tools.zip"
# Android command-line tools (Linux). sdkmanager self-updates package defs online,
# so this build can still install android-36 / build-tools 36.
CLT_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

echo "== JDK in use =="
java -version

mkdir -p "$SDK/cmdline-tools"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "== Downloading Android command-line tools =="
  curl -fsSL "$CLT_URL" -o "$CLT_ZIP"
  rm -rf "$SDK/cmdline-tools/tmp"
  mkdir -p "$SDK/cmdline-tools/tmp"
  unzip -q "$CLT_ZIP" -d "$SDK/cmdline-tools/tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$SDK/cmdline-tools/tmp" "$CLT_ZIP"
else
  echo "== command-line tools already present =="
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

echo "== Accepting licenses =="
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true

echo "== Installing platform-tools, platforms;android-36, build-tools;36.0.0 =="
"$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "platforms;android-36" "build-tools;36.0.0"

echo "== Writing env file ~/.eiliot-build-env =="
cat > "$HOME/.eiliot-build-env" <<EOF
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH"
EOF

echo "== Installed packages =="
"$SDKMANAGER" --sdk_root="$SDK" --list_installed || true
echo "=== SETUP DONE ==="
