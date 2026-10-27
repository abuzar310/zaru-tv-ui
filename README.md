# Zaru TV — Apple-TV-style makeover for a TCL Google TV

![platform](https://img.shields.io/badge/platform-Android%20TV%2012-3ddc84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![language](https://img.shields.io/badge/Java-plain%2C%20no%20AndroidX%20in%20tvx-orange)
![license](https://img.shields.io/badge/license-MIT-green)
![status](https://img.shields.io/badge/status-running%20on%20my%20TV-brightgreen)

A personal project that turns a budget **TCL G10 (Google TV 12, 1 GB RAM)** into something that
feels like an Apple TV: a cinematic home screen, a real Control Center, fluid focus animation, an
ambient glow that reacts to what's on screen, and an aerial video screensaver.

> Personal use only. This repository is a private backup of **my own** work. It does **not** contain
> any third-party app's decompiled code or APKs (see *Credits & licensing* below).

## Screenshots

| | |
|:---:|:---:|
| ![Cinematic hero](docs/media/01-hero-scrim.png)<br>**Cinematic hero + scrim** | ![Focus engine](docs/media/02-focus-lift.png)<br>**Focus engine** |
| ![Ambient glow](docs/media/03-ambient-glow.png)<br>**Ambient glow** | ![Control Center](docs/media/04-control-center.png)<br>**Control Center** |
| ![Sleep timer](docs/media/05-sleep-timer.png)<br>**Sleep timer** | ![Aerial screensaver](docs/media/06-aerial-screensaver.png)<br>**Aerial screensaver** |

## What it does

**Launcher chrome & motion**
- **Living hero** — the featured backdrop slowly zooms and pans (Ken Burns), so the home breathes.
- **Home wallpaper** — any photo as the home background, drifting gently behind the dock (set it in
  the launcher's Appearance → Background → Picture, or send it from a phone).
- **Ambient glow** — the screen rim glows with the hero's dominant colour, and shifts to a focused
  app's brand colour as you move through the grid.
- **Focus engine** — the focused tile springs up with a shadow (compositor-only, smooth on 1 GB).
- **Cinematic scrim** — a directional vignette keeps hero text legible while the art stays vivid.
- **Auto day/night** — a vivid light theme by day, an OLED-friendly dark theme at night (on schedule).

**Control Center**
- A **volume slider** bound to the media stream (D-pad adjustable, re-syncs on open).
- A **Now Playing** strip that appears while media is playing.
- A **sleep timer** (Off / 15 / 30 / 60 / 90 min) that drops the TV into StandBy when it fires.

**System**
- **iPhone-style volume HUD** — a vertical capsule that fills white from the bottom, with a speaker
  button below, shown by an accessibility overlay with **press-and-hold** ramping (replacing the
  stock one-step-per-press volume bar).
- **Photo + aerial screensaver** — your own photos StandBy-style (the photo on the right; a big brass
  clock, the date, the weather and a month calendar on the left), with a muted drone clip between them
  and every change dissolving through black. See [docs/SCREENSAVER.md](docs/SCREENSAVER.md).

## Layout

| Folder | What it is |
|---|---|
| [`launcher/`](launcher/) | My fork of the open-source **cleanyourtv** launcher (`com.gathin.tvhome`): the vibe home, show-art backdrops, glass widgets, browse deck, the **volume HUD** (`VolumeHud` + `HomeKeyService`), and the **aerial screensaver** (`AerialDreamService`). Plain Java, `minSdk` 26. |
| [`tvx-mod/`](tvx-mod/) | The **`com.zaru.tvx`** enhancement layer — the living hero, ambient glow, focus engine, Control Center additions and sleep timer. Shipped as a second dex + one hook and applied **locally** over the tvOS Launcher with apktool. See [`tvx-mod/README.md`](tvx-mod/README.md). |

## Building

- **Launcher:** JDK 17, Android SDK platform 34. `cd launcher && ./gradlew assembleDebug`.
- **tvx-mod:** see [`tvx-mod/README.md`](tvx-mod/README.md) — it is applied to a locally-decoded copy
  of the tvOS Launcher, which is **not** included here.

## Credits & licensing

- **Launcher** is a fork of **[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)**
  (MIT) — see `launcher/` for the upstream licence; my changes are released under the same terms.
- **`tvx-mod`** enhances the closed-source **tvOS Launcher** (`com.sid.tvos`) by **Siddhant**, used
  **with the developer's permission, for personal use, keeping his credit, and with no
  redistribution**. His app's code and APK are deliberately **not** included in this repo — only my
  own enhancement layer and the recipe to apply it locally.
- **Aerial clip** (`launcher/app/src/main/res/raw/aerial_loop.mp4`) is from **[Pexels](https://www.pexels.com/)**
  under the free Pexels licence.

Everything I wrote here is mine to keep and build on; the third-party pieces above remain the property
of their authors.
