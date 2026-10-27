#!/usr/bin/env bash
# Rebuild the personal tvOS replica: compile tvx -> dex, apktool build his smali (+ the one hook),
# inject tvx as classes2.dex, align and debug-sign. Output: build/tvos-replica-2.0.1.apk
set -e
cd "D:/zaru verce/cleanyourtv/tvos-mod"
BT="D:/dev/android-sdk/build-tools/34.0.0"
JDK="D:/dev/jdk-17.0.20.1+1"
AJAR="D:/dev/android-sdk/platforms/android-34/android.jar"
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
mkdir -p build

echo "[1/5] compile tvx -> dex"
rm -rf tvx-out tvx-dex && mkdir -p tvx-out tvx-dex
"$JDK/bin/javac" -source 8 -target 8 -cp "$AJAR" -d tvx-out $(find tvx-src -name '*.java') 2>/dev/null
"$JDK/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 29 --output tvx-dex --lib "$AJAR" $(find tvx-out -name '*.class')

echo "[2/5] apktool build"
rm -f build/replica-unsigned.apk build/replica-dex.apk build/replica-aligned.apk
java -jar "D:/zaru verce/tools/apktool/apktool.jar" b decoded -o build/replica-unsigned.apk 2>&1 | grep -iE "built|error|exception|invalid" | head

echo "[3/5] inject classes2.dex"
cp build/replica-unsigned.apk build/replica-dex.apk
python - <<PY
import zipfile
with zipfile.ZipFile(r"build/replica-dex.apk","a",zipfile.ZIP_STORED) as z:
    z.write(r"tvx-dex/classes.dex","classes2.dex")
with zipfile.ZipFile(r"build/replica-dex.apk") as z:
    assert "classes.dex" in z.namelist() and "classes2.dex" in z.namelist(), z.namelist()[:5]
    print("   dex entries:", [n for n in z.namelist() if n.endswith('.dex')])
PY

echo "[4/5] zipalign"
"$BT/zipalign.exe" -f -p 4 build/replica-dex.apk build/replica-aligned.apk

echo "[5/5] sign"
"$BT/apksigner.bat" sign --ks "C:/Users/crack/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out build/tvos-replica-2.0.1.apk build/replica-aligned.apk
"$BT/apksigner.bat" verify --print-certs build/tvos-replica-2.0.1.apk 2>&1 | grep -iE "SHA-256 digest" | head -1
ls -la build/tvos-replica-2.0.1.apk
echo "OK"
