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

## Volume HUD + press-and-hold (`launcher/.../VolumeHud.java`, `HomeKeyService.java`)
The launcher's accessibility service now handles the volume keys itself: every key-down (including
auto-repeat while held) nudges the media volume, so **holding ramps** instead of one step per press. A
hand-drawn tvOS-style bar is shown in a `TYPE_ACCESSIBILITY_OVERLAY` window and fades after ~1.3 s.

## Aerial screensaver (`launcher/.../AerialDreamService.java`)
A `DreamService` that plays a muted, looping, crop-to-fill cinematic clip
(`res/raw/aerial_loop.mp4`, Pexels, 720p30 H.264 — gentle on a 1 GB device). The decoder is released when
the dream detaches. Set it active with:
```
settings put secure screensaver_components com.gathin.tvhome/.AerialDreamService
```
