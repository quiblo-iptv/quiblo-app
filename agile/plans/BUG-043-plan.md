# BUG-043 Implementation Plan

## Approach

1. **Model** — `LiveFormat { AUTO, HLS, TS }` in `:core:model`; `Source.liveFormat` and
   `Source.allowedLiveFormats`.
2. **Source API** — `SourceRequest.liveFormat` / `allowedLiveFormats` (defaults keep every existing
   call site as it was); `SourceResult.Success.allowedLiveFormats`.
3. **Xtream**
   - `FlexibleStringListSerializer`; `UserInfo.allowedOutputFormats`.
   - `load` carries the authenticated user's formats through `Context` to `assemble`.
   - `XtreamUrl.liveExtension(format, allowed)`: HLS → `m3u8`, TS → `ts`, AUTO → `m3u8` when allowed,
     else `ts`. `playbackUrl` passes it to `resolve`, which only uses it for a live locator.
4. **Database** — `SourceEntity.liveFormat` (`NOT NULL DEFAULT 'AUTO'`) and `allowedLiveFormats`
   (comma-separated, nullable); `MIGRATION_25_26` adds both; `SourceDao.setAllowedLiveFormats`.
5. **Data** — mappers; `SourceRepository.store` saves reported formats (a refresh that does not
   report any leaves the last answer); `editSource(…, liveFormat)`; `ChannelRepository.playbackUrl`
   puts the source's choice and formats on the request.
6. **Screens** — a segmented Automatic / HLS / TS control on the phone's edit dialog and a chip row
   on the television's edit form, for accounts only.

## Files Touched

- `core/model/.../Media.kt`
- `source/api/.../MediaSource.kt`
- `source/xtream/.../XtreamUrl.kt`, `XtreamSource.kt`, `dto/XtreamDto.kt`, `dto/Flexible.kt`; test `XtreamLiveFormatTest.kt` (new)
- `core/database/.../entity/Entities.kt`, `dao/Daos.kt`, `Migrations.kt`, `QuibloDatabase.kt`, `schemas/…/26.json` (new)
- `core/database/src/test/.../ExportedSchema.kt`, `LiveFormatMigrationTest.kt` (new)
- `core/data/.../Mappers.kt`, `SourceRepository.kt`, `ChannelRepository.kt`; tests `SourceEditTest.kt`, `backup/BackupRepositoryTest.kt` (fake)
- `feature/sources/.../SourcesViewModel.kt`, `SourcesScreen.kt`, `res/values/strings.xml`
- `app-tv/.../sources/TvAddSourceForm.kt`, `TvSourcesScreen.kt`, `res/values/strings.xml`
- `agile/items|plans|testing/BUG-043-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: Automatic switches accounts that allow both from TS to HLS on their next refresh, and a
  panel whose HLS is worse than its TS gets worse. Mitigation: the per-source TS choice, and the
  sweep compares start time and stability on both before release. **Verify on real streams.**
- Risk: `SourceRepository.editSource` and `SourcesViewModel.editSource` cross detekt's six-parameter
  threshold; suppressed with the reason — one parameter per form field.
- Rollback: revert the branch commit is not enough once installed (schema 26). Set every source to
  TS to restore the old requests; the columns are harmless.
