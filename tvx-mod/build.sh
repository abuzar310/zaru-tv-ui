#!/usr/bin/env bash
# Compile tvx-mod, rebuild a locally decoded launcher, inject classes2.dex, align and debug-sign.
# The host APK is not in this repo. From this directory:
#   apktool d your-launcher.apk -o decoded
#   ANDROID_HOME=/path/to/sdk ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -d decoded ]]; then
  echo "decoded/ is missing. Run: apktool d your-launcher.apk -o decoded" >&2
  exit 1
fi

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK" || ! -d "$SDK/build-tools" ]]; then
  echo "Set ANDROID_HOME to your Android SDK (platform 34 and build-tools 34)." >&2
  exit 1
fi
BT="$(printf '%s\n' "$SDK"/build-tools/* | sort -V | tail -1)"
AJAR="${ANDROID_JAR:-$SDK/platforms/android-34/android.jar}"
if [[ ! -f "$AJAR" ]]; then
  echo "android-34 platform not found at $AJAR" >&2
  exit 1
fi

JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVAC" >/dev/null 2>&1 || JAVAC=javac
command -v "$JAVA" >/dev/null 2>&1 || JAVA=java

APKTOOL="${APKTOOL:-apktool}"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
[[ -x "$ZIPALIGN" ]] || ZIPALIGN="$BT/zipalign.exe"
[[ -x "$APKSIGNER" || -f "$APKSIGNER.bat" ]] || true

KS="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
if [[ ! -f "$KS" ]]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

mkdir -p build
echo "[1/5] compile tvx -> dex"
rm -rf tvx-out tvx-dex && mkdir -p tvx-out tvx-dex
find src -name '*.java' > build/sources.list
"$JAVAC" -encoding UTF-8 -source 8 -target 8 -cp "$AJAR" -d tvx-out @build/sources.list
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --output tvx-dex --lib "$AJAR" $(find tvx-out -name '*.class')

echo "[2/5] apktool build"
rm -f build/replica-unsigned.apk build/replica-dex.apk build/replica-aligned.apk
"$APKTOOL" b decoded -o build/replica-unsigned.apk

echo "[3/5] inject classes2.dex"
cp build/replica-unsigned.apk build/replica-dex.apk
python3 - <<'PY'
import zipfile
with zipfile.ZipFile("build/replica-dex.apk", "a", zipfile.ZIP_STORED) as z:
    z.write("tvx-dex/classes.dex", "classes2.dex")
with zipfile.ZipFile("build/replica-dex.apk") as z:
    names = [n for n in z.namelist() if n.endswith(".dex")]
    assert "classes.dex" in names and "classes2.dex" in names, names
    print("   dex entries:", names)
PY

echo "[4/5] zipalign"
"$ZIPALIGN" -f -p 4 build/replica-dex.apk build/replica-aligned.apk

echo "[5/5] sign"
if [[ -f "$APKSIGNER" ]]; then
  "$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out build/zaru-tvx.apk build/replica-aligned.apk
else
  "$APKSIGNER.bat" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out build/zaru-tvx.apk build/replica-aligned.apk
fi
ls -la build/zaru-tvx.apk
echo "OK  build/zaru-tvx.apk"
