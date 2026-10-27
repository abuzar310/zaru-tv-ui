# Features — how each one works

A deeper look at what's in Zaru TV and how it's built. Screenshots are in [`media/`](media/).

## Living hero (`tvx-mod/.../HeroFx.java`)
An infinite, reversing `ObjectAnimator` gently scales (1.02→1.12) and pans (±30 px) the two shelf
backdrop ImageViews, so the featured art breathes instead of sitting still. Applied per-view via a tag,
so it survives the day/night theme recreate. The host's own cross-fade between titles is left untouched.

## Ambient + brand-reactive glow (`GlowFx.java`, `GlowView.java`)
A full-screen overlay (`GlowView`) washes the screen rim with a colour via a radial gradient that's
transparent through the middle, so it never hides content. Every ~2.3 s `GlowFx` samples the hero
bitmap's most-saturated colour and cross-fades the glow there. When a known app is focused in the grid,
it instead glows that app's brand colour for a few seconds (Netflix red, Prime blue, Hotstar violet, …).

## Focus engine (`FocusFx.java`)
A global focus listener springs the newly-focused tile up (scale 1.07 + translationZ) with an overshoot,
and settles the one it left. Deliberately compositor-only — no glare or 3D tilt, which measured as heavy
jank on a 1 GB GPU. The Control Center and our own controls are excluded so they keep their own styling.

## Control Center additions (`CcExtras.java`, `SleepTimer.java`)
- **Volume slider** — a `SeekBar` bound to `STREAM_MUSIC`, placed where the D-pad can reach it, re-synced
  to the real volume whenever the panel opens.
- **Now Playing** — reads the active `MediaController` via the host's notification listener and shows the
  title/artist while something plays; hidden otherwise.
- **Sleep timer** — cycles Off / 15 / 30 / 60 / 90 on OK, schedules an exact alarm, and fires the host's
  own StandBy screen when it elapses (so nothing else needs touching).

## The gear's Control Center (`launcher/.../QuickSettingsActivity.java`, `NowPlayingCard.java`, `SleepTimer.java`)
The remote's gear opens the launcher's own panel over the current app (`HomeKeyService` catches the key).
- **Now Playing** — `MediaSessionManager.getActiveSessions` lists other apps' sessions only to an allowed
  notification listener (`MediaListener`, which reads nothing). The card follows the first session that is
  playing (else one paused that has a title): artwork or the app's icon, title, artist or app name, and a
  progress line from the session's position and duration. OK plays or pauses through its transport
  controls; Left and Right seek 10 s where the session allows `ACTION_SEEK_TO`.
- **Sleep timer** — a tile that steps Off / 15 / 30 / 60 / 90 on OK while the panel stays open. The timer
  runs in `HomeKeyService`, which is always bound and so outlives the panel. A minute before the end it lays
  a countdown card over any app (`TYPE_ACCESSIBILITY_OVERLAY`); any button or volume press in that minute
  cancels it ("Sleep timer off"). Otherwise it sends a media pause and puts the TV to sleep with the
  accessibility lock-screen action, the same as **Turn off**. Screen-off clears it, so a timer never carries
  over to the next time the TV is on.
- The panel scrolls when Now Playing and every row will not fit; the two tiles in a row share one height.

## Volume HUD + press-and-hold (`launcher/.../VolumeHud.java`, `HomeKeyService.java`)
Android TV hands the volume keys to the system before any accessibility service sees them, so the HUD
follows `VOLUME_CHANGED_ACTION` and lies over the system's own bar (an accessibility overlay sits above it)
until that bar has gone. A Bluetooth remote sends no repeats while an accessibility key filter runs, so a
held button is followed through the system key log (READ_LOGS) and repeated one step every ~90 ms after a
0.4 s hold.

## Aerial screensaver (`launcher/.../AerialDreamService.java`)
A `DreamService` that plays a muted, looping, crop-to-fill cinematic clip
(`res/raw/aerial_loop.mp4`, Pexels, 720p30 H.264 — gentle on a 1 GB device). The decoder is released when
the dream detaches. Set it active with:
```
settings put secure screensaver_components com.gathin.tvhome/.AerialDreamService
```
