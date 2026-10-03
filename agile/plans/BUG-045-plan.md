# BUG-045 Implementation Plan

## Approach

1. `FetchedBody.bytes(limit)` — a bounded raw read, for a caller that decodes for itself.
2. `SubtitleRepository.fetchProviderSubtitle(remote)` over the app's `ContentFetcher`:
   `withTimeoutOrNull(5 s)`, a `MAX_SUBTITLE_BYTES + 1` read, `decodeSubtitle`, refuse `<html`,
   then `sniffSubtitleFormat` or refuse; write to `filesDir/subtitles/provider/<sha>.<ext>`
   (newest 50 kept); return the same `SubtitleFile` pointing at the `file://` copy, or
   `Unavailable`.
3. `SubtitleFile.selectOnStart` → `SELECTION_FLAG_DEFAULT` on the sidecar, and `prepare()` clears
   text overrides and re-enables text so the flag takes effect.
4. `PlayerViewModel`: `load()` prepares with the viewer's own subtitles only and publishes the
   panel's as `offeredSubtitles` (`offered:<n>` ids). `selectTrack(SUBTITLES, "offered:…")`
   fetches; success removes the offer and prepares again at the current position with the copy
   flagged; failure marks the offer `UNAVAILABLE` and sets `SubtitleNotice.SUBTITLE_FAILED`. A
   result for a title no longer prepared is dropped.
5. `trackMenu(…, offered)` lists offers after the engine's tracks; `rememberOfferedSubtitleEntries`
   labels them, shared by both apps.
6. `Media3PlayerController.dropSubtitles()` in `onPlayerError`, folded with the live-edge rejoin and
   the HLS fallback into `recoversInPlace()`; `PlaybackState.subtitleDropped` → the same notice.

## Files Touched

- `source/api/.../ContentFetcher.kt`
- `core/model/.../SubtitleFile.kt`
- `core/data/.../SubtitleRepository.kt`, `core/data/.../di/DataModule.kt`
- `core/media/.../PlayerController.kt`, `core/media/.../Media3PlayerController.kt`
- `source/xtream/.../XtreamSource.kt` (comment only)
- `feature/player/.../PlayerViewModel.kt`, `OfferedSubtitle.kt` (new), `TrackMenu.kt`,
  `SubtitleFilePicker.kt`, `SubtitleNotice.kt`, `PlayerScreen.kt`, `res/values/strings.xml`
- `app-tv/.../TvPlayerScreen.kt`
- Tests: `ProviderSubtitleFetchTest` (new), `PlayerOffersPanelSubtitlesTest` (new), `TrackMenuTest`
- `agile/items|plans|testing/BUG-045-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: the safety net masks a genuine stream failure once, when it happens with subtitles on. It
  costs one re-prepare; the second failure is reported as usual.
- Risk: a subtitle the viewer chose restarts the film for a second. Same as attaching a file, and
  the only way the engine accepts a new track.
- Rollback: revert the branch commit.
