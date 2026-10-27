# Architecture

## tvx-mod — how the enhancement layer attaches

The host tvOS Launcher is closed-source. Rather than rewrite it, `com.zaru.tvx` rides along as a
**second dex** with a **single hook**:

1. The classes in `tvx-mod/src/com/zaru/tvx/` compile (javac + `d8`) to a dex, injected into the rebuilt
   APK as **`classes2.dex`** — Android loads every `classesN.dex` automatically on minSdk 29.
2. **One line** is added to the host `MainActivity.onCreate` smali, right after `setContentView`:
   ```smali
   invoke-static {v6}, Lcom/zaru/tvx/TvxHooks;->install(Landroid/app/Activity;)V
   ```
3. `TvxHooks.install(activity)` takes it from there: it adds one global focus listener and one
   post-layout pass, and drives every effect from those.

## Defensive by design

Every entry point is wrapped in `try/catch (Throwable)`. If anything fails — a view isn't found, an API
is missing, a permission is withheld — the effect simply doesn't appear; the host launcher never
crashes. That's what makes it safe to apply to a Home launcher you depend on.

## The pieces

| Class | Responsibility |
|---|---|
| `TvxHooks` | Entry point; wires the listeners and seeds the night dark-mode schedule. |
| `HeroFx` | Ken-Burns drift on the shelf backdrop ImageViews. |
| `GlowFx` / `GlowView` | A colour-washing overlay fed by the hero's dominant colour, and a focused app's brand colour via `brandColorOf`. |
| `FocusFx` | The global focus listener; springs the focused tile and tints its shadow with the brand colour. |
| `CcExtras` | Finds the Control Center by id and wires the volume slider + Now Playing. |
| `SleepTimer` | Control Center sleep timer — an exact alarm that fires the host's StandBy screen. |

The launcher-side additions (`VolumeHud`, `AerialDreamService`, the `HomeKeyService` volume handling)
live under `launcher/` because they belong to our own app, not the injected layer.
