# Switching the screensaver

Two screensavers ship with this project:

- **Aerial** — the cinematic drone loop, `com.gathin.tvhome/.AerialDreamService` (the default).
- **StandBy clock** — the tvOS-style clock, `com.sid.tvos/.ClockDream`.

## How to switch

This budget TV's security won't let a sideloaded app change the *active* screensaver — doing that needs
`WRITE_SECURE_SETTINGS`, which the OS withholds from non-system apps (and there is no screensaver-picker
intent that resolves on this build). So an in-UI toggle isn't reliable here; switch it over adb instead:

**Aerial:**
```
adb shell settings put secure screensaver_components com.gathin.tvhome/.AerialDreamService
```

**StandBy clock:**
```
adb shell settings put secure screensaver_components com.sid.tvos/.ClockDream
```

On a TV that *does* allow apps to write secure settings, the `ScreenSaver` tile (a Control Center toggle)
works directly — it's left out of the build here only because this TV blocks the write.

You can also try the TV's own **Settings → System → Screen saver** and pick the one you want; whether it
lists third-party screensavers depends on the launcher.
