# Building

## Launcher (`launcher/`)

Requirements: **JDK 17**, Android SDK **platform 34**, **build-tools 34.0.0**.

```
cd launcher
./gradlew assembleDebug
```

Output: `launcher/app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r <apk>`.

It's an ordinary Android TV app (`com.zaru.tv`). Set it as Home and enable its accessibility
services if you want the Home-key handling and the volume HUD:

```
adb shell settings put secure enabled_accessibility_services com.zaru.tv/.HomeKeyService
adb shell settings put secure accessibility_enabled 1
# Holding a volume button keeps moving the volume (HomeKeyService follows the system key log):
adb shell pm grant com.zaru.tv android.permission.READ_LOGS
# The gear panel's Now Playing card (Android lists other apps' media only to an allowed listener):
adb shell cmd notification allow_listener com.zaru.tv/com.zaru.tv.MediaListener
```

Aerial screensaver: `adb shell settings put secure screensaver_components com.zaru.tv/.AerialDreamService`.

## tvx-mod (`tvx-mod/`)

`tvx-mod` is applied over a **locally-decoded copy of the tvOS Launcher**, which is *not* included — you
supply your own copy that you have permission to modify. Requirements: JDK 17, Android SDK build-tools
34 (`d8`, `zipalign`, `apksigner`), and **apktool**.

1. `apktool d your-tvos-launcher.apk -o decoded`
2. Follow [`../tvx-mod/README.md`](../tvx-mod/README.md) to add the classes, resources and the one hook.
3. `build.sh` compiles the dex, rebuilds, injects it as `classes2.dex`, aligns and debug-signs.

Install updates with `adb install -r`; a same-key update keeps the app's data. After each install,
re-grant the runtime permissions and accessibility services (an install resets them).
