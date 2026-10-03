# BUG-041 Implementation Plan

## Approach

1. **Locators** (`XtreamUrl`): `liveLocator(id)`, `vodLocator(id, ext)`, `seriesLocator(id, ext)` and
   `resolve(base, user, pass, locator)`. The scheme `xtream:` is never a real URL scheme, so a
   locator cannot be mistaken for something playable, and `resolve` refuses anything malformed.
2. **Load** (`XtreamSource`): `mapLive`, `mapVod` and episode building store locators. The
   credentials stop being an input to `toSeriesDetails`.
3. **Play**: `MediaSource.playbackUrl(request, locator)` with an identity default (M3U).
   `XtreamSource.playbackUrl` reads the credential store and resolves; no network request.
   `ChannelRepository.playbackUrl(sourceId, locator)` finds the source, and `PlayerViewModel.load`
   calls it for both a channel's URL and an episode's. `PlayableItem.id` — the history key — stays
   the locator.
4. **Migration 24 → 25** (`MIGRATION_24_25`, `XtreamUrlRewrite.kt`):
   - Reads the Xtream sources' ids and hosts; does nothing on an install without one.
   - For `channels.streamUrl`, `resume_positions`, `watch_events`, `favorites`, `feed_rows`
     (`stableKey`), `picked_subtitles.stableKey` and `title_opinions.titleKey`: every value matching
     `http(s)://host/(live|movie|series)/user/pass/file` becomes its locator.
   - A channel is rewritten only when its source is Xtream — a playlist exported from the same
     panel stores the same shape of URL and must keep one it can play. A history key is rewritten
     when its row's source is Xtream, or for tables without a source, when its host is an Xtream
     source's host.
   - `UPDATE OR REPLACE`, so two old URLs for one episode (the password changed once) collapse to
     one row rather than failing the upgrade.
   - `SCHEMA_VERSION = 25`; `25.json` is `24.json` with the version changed.
5. **Tests**: the source side in `:source:xtream`, the rewrite as pure functions and as a migration
   over the exported v24 schema in `:core:database`.

## Files Touched

- `source/api/.../MediaSource.kt`
- `source/xtream/.../XtreamUrl.kt`, `XtreamSource.kt`; tests `XtreamCredentialsAtPlayTimeTest.kt` (new), `XtreamSourceTest.kt`
- `core/data/.../ChannelRepository.kt`
- `feature/player/.../PlayerViewModel.kt`
- `core/database/.../XtreamUrlRewrite.kt` (new), `Migrations.kt`, `QuibloDatabase.kt`, `schemas/…/25.json` (new)
- `core/database/src/test/.../XtreamUrlRewriteTest.kt`, `StoredUrlMigrationTest.kt` (new)
- `agile/items|plans|testing/BUG-041-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: a history row the rewrite misses keeps a credentialed key, and its episode loses its resume
  point on the next play. Mitigation: every table that can hold an episode key is listed in
  `STORED_URL_COLUMNS`, and the migration test asserts no history table keeps the password.
- Risk: a backup made by an older version and restored on this one brings credentialed keys back.
  They behave as before (history keyed by a URL nothing plays any more) until the next refresh
  replaces the catalogue; the passwords in them were already in that file.
- Rollback: **not a plain revert** once installed — a database at 25 cannot be opened by a build
  at 24 without a destructive migration. Roll forward instead; the catalogue itself is rebuilt by a
  refresh either way.
