# FEAT-031 Implementation Plan

## Approach

1. `CatalogueSyncInterval` (4 / 8 / 12 / 24 hours) in `core/model`, stored app-wide in
   `PlayerSettingsStore` beside `checkUpdatesOnLaunch` — the other setting about the device
   rather than the viewer.
2. `SyncScheduler.watch(flow)` registers on the first value with `KEEP` and re-registers on every
   later one with `UPDATE`. Opening the app must not restart a counting interval; a viewer
   choosing a number must take effect now.
3. `CatalogueSyncWorker.WORK_NAME` becomes `quiblo-catalogue-sync-v2`, and the old name is
   cancelled. `KEEP` cannot reach a job an older build registered, so without a new name every
   existing install keeps four days forever.
4. Reorder the Xtream walk: auth and the three stream lists first, then a fingerprint, then the
   three category lists only if the fingerprint has changed. `SourceResult.Unchanged` is the new
   outcome; `SourceRequest.knownFingerprint` is the new input.
5. The fingerprint is counts plus the newest `added` / `last_modified`, and a hash of the live
   ids. There is no delta endpoint in the Xtream API; this is what the lists already carry.
6. `ChannelDao.mergeForSource` compares each arriving row against the stored one, in chunks, and
   inserts only what differs.
7. `sources.catalogueFingerprint` + `MIGRATION_24_25`, `SCHEMA_VERSION = 25`.

## Files Touched

- `core/model/.../CatalogueSyncInterval.kt` (new)
- `core/datastore/.../PlayerSettingsStore.kt`, `core/data/.../PlayerSettingsRepository.kt`
- `feature/sync/.../SyncScheduler.kt`, `.../CatalogueSyncWorker.kt`
- `source/api/.../MediaSource.kt`, `source/xtream/.../XtreamSource.kt`
- `core/database/.../dao/Daos.kt`, `.../entity/Entities.kt`, `.../Migrations.kt`,
  `.../QuibloDatabase.kt`
- `core/data/.../SourceRepository.kt`
- `feature/settings/.../SyncSettingsCard.kt` (new), `.../SettingsScreen.kt`,
  `.../SettingsViewModel.kt`, `app-tv/.../TvSettingsScreen.kt`
- Both `Application` classes

## Risks & Rollback

- Risk: four-hourly against a strict panel provokes a block. Mitigation: the light pass makes an
  unchanged run cost four requests rather than seven, the interval is settable up to daily, and
  the existing `beginBackoff` still applies.
- Risk: a fingerprint collision skips a sync that had something in it. Cost is one interval — the
  next run's fingerprint differs and the full load happens then. Not a wrong catalogue.
- Rollback: revert the branch commit. The column is left behind harmlessly; nothing reads it.
