# BUG-044 Implementation Plan

## Approach

1. `PlayableItem.mimeType: String? = null` — for a caller that knows. Nothing sets it yet; the field
   is the seam for a source that learns the type from its own metadata.
2. `PlaybackDecisions.kt`:
   - `hlsMimeTypeFor(url)` — `application/x-mpegURL` when the lower-cased path ends `.m3u8`, or the
     query matches `(^|[&=.])m3u8($|&)` (a value `m3u8`, or a file named `….m3u8`); else null.
   - `retriesAsHls(errorCode, declaredMimeType, url, alreadyTried)` — true only for
     `PARSING_CONTAINER_UNSUPPORTED`, no declared type, a path extension outside the containers the
     engine knows by name, and not already tried.
3. `Media3PlayerController`:
   - `toMediaItem(mimeOverride)` sets `mimeOverride ?: mimeType ?: hlsMimeTypeFor(url)`.
   - `tryAsHls(failure)` runs in `onPlayerError` after the live-edge rejoin and before the retry
     ladder: it sets the same item again with the HLS type (at its start position for VOD, the
     default position for live) and prepares. `triedAsHls` resets on `prepare` and `retry`. The
     watchdog is not restarted, so the fallback spends the same 12 s budget.

## Files Touched

- `core/media/src/main/kotlin/dev/quiblo/core/media/PlaybackDecisions.kt`
- `core/media/src/main/kotlin/dev/quiblo/core/media/PlayerController.kt`
- `core/media/src/main/kotlin/dev/quiblo/core/media/Media3PlayerController.kt`
- `core/media/src/test/kotlin/dev/quiblo/core/media/HlsWithoutExtensionTest.kt` (new)
- `agile/items|plans|testing/BUG-044-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: a query that happens to contain `m3u8` for another reason makes a progressive file be read
  as HLS. It fails as a malformed manifest, which is what it would have failed as anyway, and the
  pattern only matches `m3u8` as a whole value or as a file extension.
- Risk: the fallback costs one more request on a genuinely unsupported stream. Once, and inside the
  budget.
- Rollback: revert the branch commit.
