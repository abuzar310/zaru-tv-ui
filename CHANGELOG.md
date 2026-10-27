# Changelog

All notable changes to Zaru TV. Dates are when the feature landed on the real TV.

## 2026-10-27

### Added — launcher chrome & motion
- **Living hero** — slow Ken-Burns drift (zoom + pan) on the featured backdrop.
- **Home wallpaper** — finishes the launcher's own *Background: Picture* option, which saved the
  picture but never drew it: with the shelf Off, any photo fills the home behind the dock and drifts.
- **Ambient glow** — the screen rim glows with the hero's dominant colour (or the wallpaper's).
- **Brand-reactive glow** — the glow shifts to a focused app's brand colour in the grid.
- **Focus engine** — the focused tile springs up with a shadow (compositor-only, smooth on 1 GB).
- **Cinematic scrim** — a directional vignette; vivid art, legible text.
- **Auto day/night** — vivid light theme by day, dark theme at night, on a sunset schedule.

### Added — Control Center
- **Volume slider** bound to the media stream, D-pad adjustable, re-syncs on open.
- **Now Playing** strip while media is playing.
- **Sleep timer** — Off / 15 / 30 / 60 / 90 min, fires the host StandBy screen.

### Added — system
- **iPhone-style volume HUD** — one tall capsule with the volume number at the top, a white fill that
  climbs as you press or hold the button, and the speaker glyph inside; it covers Android TV's own
  volume bar while that is up (accessibility overlay).
- **Aerial video screensaver** — muted, looping, licence-clean drone clips that dissolve through
  black between shots, with a soft clock, date and live weather (condition icon + today's high/low).
- **StandBy in the house design** — the launcher's StandBy (the sleep timer's screen and *Preview
  StandBy*) shows the saved Background picture with the brass clock, date, weather and gold calendar,
  matching the screensaver.
- **Photo screensaver, StandBy-style** — your own photos open the screensaver: the photo on the right
  with a slow push-in, and a big brushed-brass clock, the date, the weather and a month calendar (today
  in a brass circle) on its dark left side. One aerial clip plays between photos.

### Fixed
- The volume HUD now appears for the real remote. Android TV hands the volume keys to the system before
  any accessibility service sees them, so the HUD follows volume changes instead of keys, and lies over
  the system's volume bar until that bar has gone.
- The tvx hooks re-attach when the launcher recreates its window (it does so at the dark-mode flip at
  sunset and sunrise); before, they were wired once per process and went quiet after the first flip.

### Notes
- All of `tvx-mod` is defensive (`try/catch`) — an effect can only fail to appear, never crash the host.
- The launcher is a fork of the MIT-licensed cleanyourtv; `tvx-mod` is applied locally, with permission,
  over the tvOS Launcher and keeps its author's credit.
