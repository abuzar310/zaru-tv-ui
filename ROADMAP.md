# Roadmap

Ideas and work-in-progress for Zaru TV. Nothing here is a promise — it's where the project is heading.

## Next up
- **Content rails** — a "Continue Watching" / "Trending" row of real posters pulled from the installed
  streaming apps via the TV provider (`watch_next_program` + `preview_program`, READ_TV_LISTINGS). The
  data layer is designed; the remaining work is injecting a scrolling row into the host's custom home
  grid and wiring focus — best done carefully, on-device.
- **Glass-blur dock** — a snapshot-blurred backdrop behind the dock and Control Center (RenderEffect /
  a cached downscaled blur, to stay cheap on a 1 GB GPU).
- **App-open zoom** — the focused tile expands to fill the screen as its app launches.

## Polish
- A short curated set of aerial clips that shuffle, with place names.
- Per-app taglines on the home (needs a layout that has room for them).
- Reduced-motion and per-viewer options.

## Done
See [`CHANGELOG.md`](CHANGELOG.md).
