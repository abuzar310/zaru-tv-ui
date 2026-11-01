# Zaru OS

Zaru TV and Halo TV, merged. A home screen for Android TV, Google TV and Fire TV: cinematic dock, vibe home, Control Center, Ambient, Jarvis, and Tonight.

![platform](https://img.shields.io/badge/platform-Android%20TV%20%2F%20Google%20TV%20%2F%20Fire%20TV-3ddc84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![language](https://img.shields.io/badge/Java-17-orange)
![license](https://img.shields.io/badge/license-MIT-green)

Two new packages, so the shipping **Zaru TV 1.2** (`com.zaru.tv`) on your set is never overwritten. You can install both and switch Home between them.

| App | Package | Version | What it is |
|---|---|---|---|
| **Home** | `com.zaru.os` | 1.0 | Cinematic dock, vibe home, Control Center, volume HUD, aerial screensaver, **Ambient + Jarvis**, optional Glass / Waves |
| **Tonight** | `com.zaru.tonight` | 1.0 | Films and series across *your* apps (TMDB + JustWatch + optional Claude) |

Shared files were merged once — not copied twice. How: [MERGE.md](MERGE.md). An AI agent can install this for you: [AGENTS.md](AGENTS.md).

The **shipping, key-free** home stays in [abuzar310/zaru-tv-ui](https://github.com/abuzar310/zaru-tv-ui). Use that if you only want the cinematic dock and Control Center.

## Contents

- [What you get](#what-you-get)
- [Zaru OS vs Zaru TV](#zaru-os-vs-zaru-tv)
- [Install with an AI agent](#install-with-an-ai-agent)
- [Install by hand](#install-by-hand)
  - [1. What you need](#1-what-you-need)
  - [2. Turn on ADB debugging on the TV](#2-turn-on-adb-debugging-on-the-tv)
  - [3. Get your keys](#3-get-your-keys)
  - [4. Run the installer](#4-run-the-installer)
  - [5. First run](#5-first-run)
- [Using Zaru OS](#using-zaru-os)
- [Jarvis relay and Alexa (optional)](#jarvis-relay-and-alexa-optional)
- [Customise](#customise)
  - [Home look](#home-look)
  - [Country and streaming apps](#country-and-streaming-apps)
- [Uninstall](#uninstall)
- [Troubleshooting](#troubleshooting)
- [Keys and privacy](#keys-and-privacy)
- [Project layout](#project-layout)
- [License](#license)

## What you get

**Default look** is Zaru cinematic (`cinematic_home=true`, `glass_home=false`, `settings_key_panel=true`). Glass / Waves is there; it is off until you turn it on. Ambient is the dock tile for this package. **TONIGHT.** appears in the top bar when that APK is installed.

### From Zaru TV

- **Cinematic home** (`MainActivity`). Focused dock art fills the top of the screen, with a slow Ken Burns drift and a scrim so the name stays readable. When a streaming app has published cards, the shelf uses a real poster (`ShowArt`, needs `READ_TV_LISTINGS`).
- **Vibe home** (`VibeHomeActivity`). Full-bleed black-and-white stills from those same listings, a thin clock with the weather, white logos, one line of text for the focused app. Down opens the classic grid.
- **Glass dock.** First row is `dock_apps` in `config.xml`. Focused tiles spring up (scale 1.18). Compositor-only — no 3D tilt that janks a 1 GB GPU.
- **Control Center.** `HomeKeyService` catches the gear key and opens `QuickSettingsActivity` over whatever is on screen: Now Playing (OK plays or pauses, Left / Right skip 10 s), sleep timer (Off / 15 / 30 / 60 / 90), Turn off, Wi-Fi, inputs, sound, display, TV settings, apps.
- **Volume HUD.** One tall iPhone-style capsule over Android TV’s own bar. Hold-to-repeat uses the system key log (`READ_LOGS`).
- **Aerial screensaver.** Three `DreamService`s: `AerialDreamService` (your photos StandBy-style + a muted Pexels aerial), `AerialDream` (Apple aerial files in `files/aerials/`), `ClockDream` (light clock on black).
- **Weather.** Open-Meteo card from `weather_city` / `weather_lat` / `weather_lon`. No location permission required for the widget; Ambient can also ask a public IP-location service unless you set a place on the phone web app.

### From Halo TV

- **Ambient.** Always-on screen (`AmbientActivity`): clock, sunrise/sunset, weather, air, a turntable player and Jarvis. The screen stays on while it is open.
- **Phone web app.** Port **8765** on the TV (`RemoteServer`). Scan the QR code on Ambient, or open `http://<TV-IP>:8765` on the same Wi-Fi. Music search and queue, Jarvis, notes, settings. The phone and the TV page never talk directly: the phone posts commands, the TV page syncs them.
- **Jarvis.** Pattern replies on the TV; optional OpenAI for a natural voice (key entered on the phone, stored only on the TV); optional Alexa via a Cloudflare relay.
- **Tonight.** Genre wheel, For You (Claude + a local taste graph of what you open), search, Marvel / DC Hall of Heroes. Opens the title in the right app from JustWatch.
- **Glass Home + Waves.** `glass_home` in `config.xml` (off by default). White logos, Midnight glass, optional `assets/waves.mp4` backdrop. Glass and the aerial loop are mutually exclusive: `glass_home=true` skips `AerialBackdrop`.
- **Fire TV.** Fire OS will not let another app own Home. The Amazon launcher stays; `HomeKeyService` sends the Home button here. `--keep-stock` does the same on Google TV / Android TV.

## Zaru OS vs Zaru TV

| | [Zaru TV](https://github.com/abuzar310/zaru-tv-ui) | This repo |
|---|---|---|
| Package | `com.zaru.tv` 1.2 | `com.zaru.os` 1.0 + `com.zaru.tonight` 1.0 |
| Install | Website APK or `cd launcher && ./gradlew` | `./install.sh` (Home + Tonight) |
| API keys | None | TMDB required for Tonight; Anthropic / OpenAI optional |
| Ambient / Jarvis / Tonight | No | Yes |
| Glass / Waves | No | Yes (off by default) |
| Fire TV Home redirect | No | Yes |
| Shipping status | The product on the TV today | Experiment. Sit it next to 1.2 and compare |

Both are MIT Java 17, `minSdk` 26, `compileSdk` / `targetSdk` 34. Shared ancestor: [cleanyourtv](https://github.com/Gathin23/cleanyourtv).

## Install with an AI agent

Open Claude Code, Codex, Cursor, or ChatGPT with a terminal, and paste:

```text
Clone https://github.com/abuzar310/zaru-os and install Zaru OS on my TV.
Follow AGENTS.md in the repo step by step, and ask me for anything you need.
I live in <your country>. My streaming apps are <e.g. Netflix, JioHotstar, Apple TV+>.
```

The agent reads [AGENTS.md](AGENTS.md), checks tools, asks for the TV IP and keys, builds both apps and installs them. You do two things on the TV: turn on ADB debugging ([step 2](#2-turn-on-adb-debugging-on-the-tv)) and press **OK** on “Allow USB debugging?”.

Existing Zaru TV 1.2 can stay installed.

## Install by hand

### 1. What you need

| | |
|---|---|
| **A TV** | Android TV or Google TV (Android 8.0+), or a Fire TV / Fire TV Stick / Fire TV Edition set |
| **A computer** | macOS or Linux on the same Wi-Fi (Windows: WSL) |
| **JDK 17** | macOS: `brew install openjdk@17` · Ubuntu: `sudo apt install openjdk-17-jdk` |
| **Android SDK** | macOS: `brew install --cask android-commandlinetools` · or Android Studio. Then `yes \| sdkmanager --licenses` |
| **Python 3** | For `tools/tv.py` (no separate `adb` install) |
| **Node.js 18+** | Only for the optional [Jarvis relay](#jarvis-relay-and-alexa-optional) |

### 2. Turn on ADB debugging on the TV

<details open>
<summary><b>Google TV / Android TV</b> (Sony, TCL, Hisense, Philips, Xiaomi, Chromecast with Google TV, NVIDIA Shield…)</summary>

1. **Settings → System → About.**
2. Click **Android TV OS build** **7 times**, until it says you are a developer.
3. **Settings → System → Developer options** → **USB debugging** on. Turn on **Network debugging** / **ADB over network** if those exist.
4. TV IP: **Settings → Network & Internet → (your Wi-Fi)**.

</details>

<details open>
<summary><b>Fire TV</b> (Fire TV Stick, Cube, Edition / Omni)</summary>

1. **Settings → My Fire TV** (or **Device & Software**) **→ About**.
2. Click the TV’s name **7 times**.
3. **Developer Options → ADB debugging** on.
4. TV IP: **Settings → Network**, highlight the Wi-Fi and press **Play/Pause**.

</details>

### 3. Get your keys

Typed into the installer (hidden) and written only to `tonight/secrets.properties`, which git ignores. See [`tonight/secrets.properties.example`](tonight/secrets.properties.example). **Never commit that file.**

| Key | Needed for | Where | Cost |
|---|---|---|---|
| **TMDB API Read Access Token** | Tonight | [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api) — the long *API Read Access Token*, not the v3 key | Free |
| **Anthropic API key** | Tonight’s For You + taste memory | [console.anthropic.com/settings/keys](https://console.anthropic.com/settings/keys) | Pay as you go |
| **OpenAI API key** | Jarvis’s natural voice | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) — later, on the phone web app, not in the installer | Pay as you go |

Home + Ambient install with no keys. Skip the TMDB prompt and Tonight is skipped.

### 4. Run the installer

```bash
git clone https://github.com/abuzar310/zaru-os.git
cd zaru-os
./install.sh
```

Seven steps:

1. Checks JDK 17, Android SDK, Python.
2. Connects to the TV (`--tv` or `TV_HOST=`).
3. Asks for keys, country (it suggests the TV’s locale) and streaming apps (TMDB list for that country, `*` on apps already installed). Writes `tonight/secrets.properties` mode 600.
4. Builds `home/` and `tonight/` release APKs.
5. Installs Home + Ambient and hands over Home:
   - **Google TV / Android TV:** this package becomes Home; a known stock launcher is disabled (not deleted). Automatic rollback if the handover does not stick.
   - **Fire TV** or `--keep-stock:** stock launcher stays; `HomeKeyService` redirects Home.
   Grants `READ_LOGS`, `READ_TV_LISTINGS`, `ACCESS_FINE_LOCATION`, the media notification listener, and sets `AerialDreamService` as the screensaver.
6. Pushes `assets/waves.mp4` (skip with `--no-video`).
7. Installs Tonight.

```bash
./install.sh --tv 192.168.1.50          # skip the IP prompt
./install.sh --home-only                # Home + Ambient only
./install.sh --tonight-only             # Tonight only
./install.sh --keep-stock               # never disable the stock launcher
./install.sh --input-key-service        # also route the remote’s Input button
./install.sh --no-video                 # skip Waves
TV_HOST=192.168.1.50 REGION=IN SERVICES="Netflix, JioHotstar" ./install.sh --yes
```

`--yes` never prompts. Missing optional keys are skipped. Re-running reinstalls over the existing apps.

If this folder still lives inside [zaru-tv-ui](https://github.com/abuzar310/zaru-tv-ui), run the same commands from `zaru-os/` — do not build `launcher/` here; that tree is still `com.zaru.tv`.

### 5. First run

- First connect: the TV shows **Allow USB debugging?** Tick **Always allow** and press **OK**. Miss it, run again.
- Press **Home**: cinematic Zaru OS opens.
- **AMBIENT** is the last dock tile (this package). **TONIGHT.** is in the top bar.
- Tonight’s search index builds in the background over the first hour; browsing works immediately.

Switch looks without a rebuild:

```bash
# Classic cinematic (default after install.sh)
adb shell cmd package set-home-activity com.zaru.os/.MainActivity
# Vibe home
adb shell cmd package set-home-activity com.zaru.os/.VibeHomeActivity
```

## Using Zaru OS

| | |
|---|---|
| **Home** | Arrows move the dock and grid. **TONIGHT.** opens Tonight. **Inputs**, **Wi-Fi**, **Settings** sit next to it. Hold **OK** on Settings for launcher settings. Gear opens the Control Center over the current app. |
| **Ambient** | Dock tile for `com.zaru.os`. Leave it on as an always-on display. |
| **Phone web app** | `http://<TV-IP>:8765` or the QR on Ambient. Music, Jarvis, notes, Settings (weather place, OpenAI key). |
| **Jarvis** | Weather, music, timers (“remind me in 20 minutes”), volume. With an OpenAI key it speaks; without one it uses the TV’s voice. |
| **Tonight** | Spin the genre wheel, **For You**, search, **MARVEL** / **DC**. **Play on…** opens the title in that app. |
| **Screensaver** | Sideloaded apps often cannot change the active dream themselves. `install.sh` sets `AerialDreamService`. Or **Settings → System → Screen saver**. |

## Jarvis relay and Alexa (optional)

Jarvis already works from the phone on your Wi-Fi. The relay is a free [Cloudflare Worker](https://workers.cloudflare.com/) so Alexa and a phone away from home can reach the TV.

```bash
jarvis/setup-relay.sh --tv 192.168.1.50
```

Needs Node.js 18+. A browser opens for Cloudflare login. It deploys `jarvis/relay`, generates `TV_KEY` and `PHONE_KEY`, stores them as Worker secrets, and pairs the TV. Nothing secret is written into the repo.

### Alexa skills

Needs a free [Amazon developer account](https://developer.amazon.com/alexa/console/ask).

1. Run `jarvis/setup-relay.sh` first. It writes `.halo/alexa/jarvis-skill.json` and `.halo/alexa/tonight-skill.json` (git-ignored).
2. `npm install -g ask-cli && ask configure`
3. Create each skill and upload its model:
   ```bash
   ask smapi create-skill-for-vendor --manifest "file:.halo/alexa/jarvis-skill.json"
   ask smapi set-interaction-model --skill-id <skill-id> --stage development --locale en-GB \
     --interaction-model "file:jarvis/alexa/jarvis/en-GB.json"
   ```
   Repeat with `tonight-skill.json` and `jarvis/alexa/tonight/en-GB.json`. Models are `en-GB`. For `en-IN` / `en-US` / `en-AU` / `en-CA`, rename the locale keys in the manifests and pass that `--locale`.
4. Tell the relay which skills to trust:
   ```bash
   cd jarvis/relay
   npx wrangler@4 secret put SKILL_ID
   npx wrangler@4 secret put TONIGHT_SKILL_ID
   ```
5. Alexa app → **Your Skills → Dev**. Then: *“Alexa, ask Jarvis to play some chill music”*, or *“Alexa, ask Tonight for something like Loki”*.

## Customise

### Home look

Edit [`home/app/src/main/res/values/config.xml`](home/app/src/main/res/values/config.xml), then `./install.sh --home-only`.

| Key | What it does |
|---|---|
| `dock_apps` | Dock order. Missing packages are skipped. `com.zaru.os` opens Ambient, not Home. |
| `hidden_apps` | Never shown as tiles. `com.zaru.tonight` is hidden; the top-bar button opens it. |
| `columns` | Tiles per row (6 in this build). |
| `cinematic_home` | Softened art shelf (on). |
| `glass_home` | Waves + white logos (off). Mutually exclusive with the aerial loop. |
| `glass_backdrop` | `video` / `lens` / `still` |
| `glass_accent` / `glass_background` | Midnight is `#FF5B8CFF` on `#FF040914`. |
| `glass_widgets` | Large clock + weather (on). |
| `weather_city` / `weather_lat` / `weather_lon` | Open-Meteo place. Change all three. Default is Chitradurga. |
| `settings_key_panel` | Gear opens the Control Center (on). |
| `reduced_motion` | Stiffer springs, shorter fades. |
| `vibe_taglines` | One-liners on the vibe home (`package\|line`). |
| `live_tv_package` | Live TV app at the end of Inputs. |

Replace `assets/waves.mp4` with any looping 960×540 video.

### Country and streaming apps

Tonight’s country and apps live in git-ignored `tonight/secrets.properties`:

```properties
region=IN
services=Netflix:8,175; Amazon Prime Video:119,2100; JioHotstar:2336; Zee5:232
```

- **`region`**: 2-letter code (`US`, `GB`, `IN`, `DE`, `CA`, `AU`…). Titles and providers follow it. The search index rebuilds when it changes.
- **`services`**: `name:TMDB provider ids[:Android TV package]`, separated by `;`. Order is the chip order.

```bash
REGION=IN ./install.sh --tonight-only
SERVICES="Netflix, Apple TV, Hulu" ./install.sh --tonight-only
```

| | Services | Opens a title |
|---|---|---|
| Built in | Netflix, Prime Video, Disney+, JioHotstar, Apple TV+, Max, Hulu, Paramount+, Peacock, ZEE5, Sony LIV, Crunchyroll, MUBI | JustWatch link, or the app’s own search |
| Any other | Whatever TMDB lists in that country | JustWatch when the app takes it; else the app opens. Needs the package as the third part |

Adding a service by hand:

1. `TMDB_TOKEN=*** python3 tools/services.py IN --pick "apple tv"` (read the token from `secrets.properties`; do not put it on a command you show).
2. For a service that is not built in: `TV_HOST=<ip> tools/venv/bin/python tools/tv.py "pm list packages"` and add the package: `BBC iPlayer:38:bbc.iplayer.android`.
3. `./install.sh --tonight-only`.

Built-in colours, packages and links: `tonight/app/src/main/java/com/zaru/tonight/Services.java`.

## Uninstall

```bash
./uninstall.sh --tv 192.168.1.50
```

Re-enables the stock launchers this installer may have disabled, drops this package’s accessibility services, uninstalls `com.zaru.os` and `com.zaru.tonight`. Zaru TV 1.2, if it was there, is left alone.

## Troubleshooting

| Problem | Fix |
|---|---|
| `could not connect to …:5555` | ADB debugging off, wrong IP, or different network. On Google TV also check **Network debugging**. |
| No “Allow USB debugging?” | It may be behind the current app: press **Home**. Or revoke old authorisations in Developer options and run again. |
| Home button still opens the old home | Fire TV / `--keep-stock`: turn on **Zaru OS** in **Settings → Accessibility** and run the installer again. Google TV: `./install.sh` reports where Home resolves. |
| `Home currently resolves to …setupwraith` | Google’s setup wizard is claiming Home. Use `./install.sh --keep-stock`. |
| No Waves behind the glass | `glass_home` is off by default. Set it true and run `./install.sh --home-only`, or `--no-video` was passed. |
| Tonight is empty | TMDB token missing or wrong. Fix `tonight/secrets.properties` and `./install.sh --tonight-only`. |
| Wrong country’s titles | `REGION=XX ./install.sh --tonight-only`. Search rebuilds in a few minutes. |
| A service is missing | [Country and streaming apps](#country-and-streaming-apps). |
| “\<Service> isn’t installed on this TV” | Install the app, or add its package as the third part of `services=`. |
| No AI picks in Tonight | Anthropic key in `secrets.properties`, then `--tonight-only`. |
| Phone web app won’t open | Same Wi-Fi. `http://<TV-IP>:8765`. |
| Build fails with a licence error | `yes \| sdkmanager --licenses`. |
| JDK / SDK not found | Set `JAVA_HOME` and `ANDROID_HOME`, then run again. |
| This overwrote Zaru TV | It cannot. Different packages. If Home now opens Zaru OS and you want 1.2 back: `cmd package set-home-activity com.zaru.tv/.VibeHomeActivity`. |

## Keys and privacy

- **No key is in this repository.** Installer → `tonight/secrets.properties` (git-ignored).
- TMDB and Anthropic keys are built into **your** Tonight APK. Do not share that APK.
- The OpenAI key is entered on the phone web app and stored **only on the TV**.
- Relay keys are generated on your computer and stored as Cloudflare Worker secrets and on the TV.
- The ADB key is created in `tools/adbkey/` (git-ignored).
- Ambient may ask a public IP-location service for the town, unless you set a place on the phone.
- Home talks to Open-Meteo for weather. Tonight talks to TMDB, JustWatch, and Anthropic if you gave a key. Jarvis talks to OpenAI if you gave a key.
- `allowBackup` is false on both apps.

## Project layout

```
home/           Home + Ambient + Jarvis server   (com.zaru.os)
tonight/        Tonight                          (com.zaru.tonight)
jarvis/         Cloudflare relay + Alexa models
tools/          Wi-Fi ADB client (tv.py), TMDB service picker
tvx-mod/        optional motion layer for another launcher (not redistributed)
assets/         Waves backdrop
install.sh      builds and installs both apps
uninstall.sh    puts the stock launcher back
AGENTS.md       step-by-step for an AI agent
MERGE.md        which shared files came from which repo
```

`tools/tv.py` is the ADB client: `TV_HOST=<ip> tools/venv/bin/python tools/tv.py '<shell command>'`. `--probe` prints the model and Android version.

## License

MIT. See [LICENSE](LICENSE).

- Copyright 2026 Abuzar (Zaru TV / Zaru OS)
- Copyright 2026 Kartik Modi (Halo TV portions: Ambient, Jarvis, Tonight, Glass Home, installer)
- Copyright 2026 Gathin and the [cleanyourtv](https://github.com/Gathin23/cleanyourtv) authors

Bundled fonts are under the SIL Open Font License. The aerial clip is from [Pexels](https://www.pexels.com/). App names, logos and film/series artwork belong to their owners. This project is not affiliated with any streaming service, Amazon, Google, Marvel or DC.
