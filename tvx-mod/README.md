# tvx-mod — the `com.zaru.tvx` enhancement layer

A tiny, self-contained layer that adds Apple-TV-style motion and a richer Control Center **on top of**
the tvOS Launcher (`com.sid.tvos`) — without changing its look or removing the author's credit. Used
**with the developer's permission, for personal use only.**

Everything here is wrapped in `try/catch`, so a failure can only make an effect quietly not appear —
the host launcher can never crash because of it.

## What's in here

| File | Effect |
|---|---|
| `TvxHooks.java` | The single entry point, called from `MainActivity.onCreate` once per window (the host recreates the activity at the dark-mode flip). Wires up everything and seeds the night dark-mode schedule. |
| `HeroFx.java` | **Living hero** — an infinite Ken-Burns drift on the two shelf backdrop ImageViews. |
| `HomeWallpaper.java` | **Home wallpaper** — draws the picture the host's *Background: Picture* setting saves (2.0.1 stores it but never shows it): with the shelf Off it sits just above `shelfGroup`, under the scrim and dock, with a slow drift. |
| `GlowFx.java` + `GlowView.java` | **Ambient glow** — samples the hero's dominant colour (and a focused app's brand colour) and washes the screen rim with it. |
| `FocusFx.java` | **Focus engine** — the focused tile springs up (scale + shadow) with an overshoot. |
| `CcExtras.java` | Control Center **volume slider** (STREAM_MUSIC) and **Now Playing** strip. |
| `SleepTimer.java` | Control Center **sleep timer** (Off / 15 / 30 / 60 / 90) that fires the host's own StandBy screen via an exact alarm. |
| `StandByHouse.java` + `StandByCalendar.java` | **StandBy in the house design** — the host's StandBy (and its StandBy screensaver) shows the saved Background picture with a slow drift, and on its left a brushed-brass clock, the date, the weather from the host's cache and a gold month calendar. With no picture saved it is left alone. |
| `res/` | The new drawables and ids the Control Center additions reference; Outfit (`font/tvx_outfit*`, SIL OFL) and thin weather icons (`drawable/tvx_wx_*`, Lucide, ISC) for StandBy. |
| `build.sh` | Compiles the above to a dex, rebuilds, injects it, aligns and debug-signs. |

## How it's applied (locally)

The tvOS Launcher's own code and APK are **not** in this repo. You apply this layer to **your own
local copy** of the app you have permission to modify:

1. Decode your copy: `apktool d tvOS_Launcher.apk -o decoded`
2. Compile `src/com/zaru/tvx/*.java` to a dex (JDK 17 + `d8`), and drop `res/` into `decoded/res/`.
3. Add **one** line to `decoded/smali/.../MainActivity.smali`, in `onCreate` right after
   `setContentView`, passing the activity:
   ```smali
   invoke-static {v6}, Lcom/zaru/tvx/TvxHooks;->install(Landroid/app/Activity;)V
   ```
   and add the Control Center rows to `decoded/res/layout/control_center.xml`. For StandBy, add one line
   before the last `return-void` of `StandByActivity.onCreate` and one after `setContentView` in
   `ClockDream.onAttachedToWindow`:
   ```smali
   invoke-static {p0}, Lcom/zaru/tvx/TvxHooks;->standby(Landroid/app/Activity;)V
   invoke-static {p0}, Lcom/zaru/tvx/TvxHooks;->standbyDream(Landroid/service/dreams/DreamService;)V
   ```
4. `apktool b`, inject the dex as `classes2.dex` (native multidex), `zipalign`, debug-sign, install.

`build.sh` automates steps 2–4 against a `decoded/` folder you produced in step 1.

## Credit

The tvOS Launcher is by **Siddhant**. This layer only adds to it and keeps his credit intact. Don't
redistribute his app or its decompiled code.
