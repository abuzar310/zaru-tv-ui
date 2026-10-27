# Switching the screensaver

Two screensavers ship with this project:

- **Aerial** — your own photos StandBy-style plus cinematic drone clips,
  `com.gathin.tvhome/.AerialDreamService` (the default). See *Your own photos* below.
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

## Your own photos

When the aerial screensaver finds pictures in its own folder it opens on them, StandBy-style: the photo on
the right with a slow push-in, and on its dark left side a big brushed-brass clock, the date, the weather
and a month calendar in champagne gold. One aerial clip plays between photos, under the small white clock,
and every change dissolves through black. With the folder empty it is the plain aerial screensaver.

The folder belongs to the app, so it needs no storage permission and the photos never leave the TV:

```
adb shell mkdir -p /sdcard/Android/data/com.gathin.tvhome/files/screensaver
adb push our-home.jpg /sdcard/Android/data/com.gathin.tvhome/files/screensaver/01-our-home.jpg
adb shell chmod 2775 /sdcard/Android/data/com.gathin.tvhome/files/screensaver
```

Photos play in name order (`01-…`, `02-…`). Use landscape 16:9 pictures, 1920×1080 or larger, with the
subject on the right and calmer, darker space on the left where the clock and calendar sit. Personal photos
stay out of this repository.

The launcher's **StandBy** uses the same design. Its photo is `standby.jpg` in the tvOS Launcher's own
folder (the launcher creates it on start), else the Background picture saved in its settings:

```
adb push our-home.jpg /sdcard/Android/data/com.sid.tvos/files/standby.jpg
adb shell chmod 644 /sdcard/Android/data/com.sid.tvos/files/standby.jpg
```
