# How the merge was done

The shipping Zaru TV tree (`../launcher`, `com.zaru.tv`) was **not edited**. This folder is a new product with new package ids so both can sit on one TV.

## Shared core (same ancestor: cleanyourtv / Gathin)

36 Java files existed in both repos at 73–99% overlap. **Zaru's copy is the base** (Control Center, volume HUD, ShowArt, aerials, focus motion). Halo-only behaviour was folded in, not pasted as a second copy.

| File | Kept from | Added from the other |
|---|---|---|
| `Config.java` | Zaru | Halo `glass_home` / logos / Waves keys |
| `MainActivity.java` | Zaru cinematic + aerial + weather | Halo `setUpGlassBackdrop`, **TONIGHT.** button |
| `AppAdapter.java` | Zaru `TvFocusMotion` + ambient colour | Halo Ambient tile + `bindGlassArt` |
| `HomeKeyService.java` | Zaru volume HUD + sleep timer | Halo Fire TV redirect, `RemoteServer`, Alexa/Ambient restore |
| `QuickSettingsActivity.java` | Zaru Control Center (full) | Halo's 5-row stub was dropped |
| `DockDecoration.java` | Zaru liquid-glass band | Halo `useGlass` / `bandRect` for the Waves lens |
| `GlassView.java` | Zaru tinted rim | Halo's sibling `GlassDrawable` kept for Glass Home tiles |

## Halo-only (ported, package → `com.zaru.os`)

`AmbientActivity`, `RemoteServer`, `JarvisLink`, `JarvisMemory`, `OpenAi`, `News`, `TurntableView`, `BlobView`, `GlassBackdrop`, `GlassDrawable`, `MotionVideo`, `MicTest`, `SongName`, plus `assets/ambient/`, Waves, Tonight (`com.zaru.tonight`), `jarvis/`, `install.sh`.

## Zaru-only (kept)

`VibeHomeActivity`, `BrowseDeckActivity`, `ShowArt`, `Aerial*`, `VolumeHud`, `SleepTimer`, `NowPlayingCard`, `MediaListener`, `TvFocusMotion`, `Weather`, `GlowView`, `NavSound`, `tvx-mod`.

## Defaults

- `cinematic_home=true`, `glass_home=false`, `settings_key_panel=true`
- Dock includes `com.zaru.os` (Ambient). Tonight is hidden from the grid; the top-bar button opens it.
- Installer sets Home to `MainActivity` and grants listings / logs / media listener / aerial screensaver.

Conflicting backdrops: Glass Home and the aerial loop are mutually exclusive. `glass_home=true` skips `AerialBackdrop`.
