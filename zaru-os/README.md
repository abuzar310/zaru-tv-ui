# Zaru OS

Zaru TV and Halo TV, merged. A new package so the shipping **Zaru TV** (`com.zaru.tv`) on your set stays untouched.

| App | Package | What it is |
|---|---|---|
| Home | `com.zaru.os` | Cinematic dock, vibe home, Control Center, volume HUD, aerial screensaver, **Ambient + Jarvis**, optional Glass / Waves |
| Tonight | `com.zaru.tonight` | Films and series across *your* apps (TMDB + JustWatch + optional Claude) |

Both are MIT. Shared files were merged once — not copied twice. See [MERGE.md](MERGE.md).

## What you get

**From Zaru TV**
- Cinematic home with `ShowArt` from the TV provider (`READ_TV_LISTINGS`)
- Vibe home (B&W posters, white logos) — `cmd package set-home-activity com.zaru.os/.VibeHomeActivity`
- Control Center on the gear key: Now Playing, sleep timer, Turn off
- iPhone-style volume HUD + hold-repeat
- Aerial screensaver (photos + Pexels loop + Apple aerials)
- Weather widget, focus motion, brand glow

**From Halo TV**
- Ambient always-on screen + phone web app on port 8765
- Jarvis (patterns, optional OpenAI, optional Alexa relay)
- Tonight (genre wheel, For You, Hall of Heroes)
- Glass Home + Waves backdrop (`glass_home` in `home/app/src/main/res/values/config.xml`, off by default)
- Fire TV Home-button redirect
- One-command `./install.sh` (and [AGENTS.md](AGENTS.md) for an AI agent)

**Default look** is Zaru cinematic (`cinematic_home=true`, `glass_home=false`, `settings_key_panel=true`). Turn on Waves with `glass_home=true`. Ambient sits on the dock as this package; **TONIGHT.** appears in the top bar when that APK is installed.

## Install

Existing Zaru TV 1.2 can stay installed. This is `com.zaru.os`.

```bash
cd zaru-os
./install.sh
```

Needs JDK 17, Android SDK, Python 3, the TV on the same Wi-Fi with ADB debugging. Tonight needs a free [TMDB](https://www.themoviedb.org/settings/api) read token. Anthropic / OpenAI are optional.

```bash
./install.sh --home-only          # skip Tonight
./install.sh --keep-stock         # Fire TV / leave the stock launcher
./install.sh --tv 192.168.1.50
```

Uninstall puts the stock launcher back:

```bash
./uninstall.sh --tv 192.168.1.50
```

## Split into its own GitHub repo

This folder is self-contained. Existing `launcher/` and `tvx-mod/` in the parent repo are the old Zaru TV and were not modified.

```bash
# from the parent repo root, after this lands
git subtree split -P zaru-os -b zaru-os
# create an empty repo on GitHub, then:
git push git@github.com:<you>/zaru-os.git zaru-os:main
```

## Layout

```
home/       Home + Ambient + Jarvis server   (com.zaru.os)
tonight/    Tonight                          (com.zaru.tonight)
jarvis/     Cloudflare relay + Alexa models
tools/      Wi-Fi ADB client, TMDB service picker
tvx-mod/    optional overlay for another launcher
assets/     Waves backdrop
install.sh  builds and installs both apps
```
