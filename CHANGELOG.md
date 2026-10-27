# Changelog

All notable changes to Zaru TV. Dates are when the feature landed on the real TV.

## 2026-10-27

### Added — launcher chrome & motion
- **Living hero** — slow Ken-Burns drift (zoom + pan) on the featured backdrop.
- **Ambient glow** — the screen rim glows with the hero's dominant colour.
- **Brand-reactive glow** — the glow shifts to a focused app's brand colour in the grid.
- **Focus engine** — the focused tile springs up with a shadow (compositor-only, smooth on 1 GB).
- **Cinematic scrim** — a directional vignette; vivid art, legible text.
- **Auto day/night** — vivid light theme by day, dark theme at night, on a sunset schedule.

### Added — Control Center
- **Volume slider** bound to the media stream, D-pad adjustable, re-syncs on open.
- **Now Playing** strip while media is playing.
- **Sleep timer** — Off / 15 / 30 / 60 / 90 min, fires the host StandBy screen.

### Added — system
- **iPhone-style volume HUD** — a vertical capsule that fills white from the bottom with a speaker
  button below, and **press-and-hold** ramping (accessibility overlay).
- **Aerial video screensaver** — muted, looping, licence-clean drone clips that dissolve through
  black between shots, with a soft clock, date and live weather (condition icon + today's high/low).

### Notes
- All of `tvx-mod` is defensive (`try/catch`) — an effect can only fail to appear, never crash the host.
- The launcher is a fork of the MIT-licensed cleanyourtv; `tvx-mod` is applied locally, with permission,
  over the tvOS Launcher and keeps its author's credit.
