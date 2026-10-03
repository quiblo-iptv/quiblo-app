# BUG-037 Implementation Plan

## Approach

1. `PlayerController.stop()`; `Media3PlayerController.stop()` cancels the retry and the watchdog
   and calls `player.stop()`. Restarting is the existing `retry()`, which since `BUG-036` restarts a
   live item at the live edge.
2. `Media3PlayerController.prepare()` calls `player.stop()` before `setMediaItem` when the item it
   is replacing was live.
3. `PlayerViewModel`
   - `onStopped()` — live: `stop()` and remember that it did; otherwise `pause()`. Position and
     watch log written as before.
   - `onStarted()` — if `onStopped()` let a live channel go, `retry()`. A no-op otherwise, which
     matters because a lifecycle observer receives `ON_START` once when it is added.
   - `onLeft()` — the television's dispose path: like `onStopped()`, but a live request is
     forgotten (`loadedRequest = null`) rather than marked for restart.
   - `load()` clears the remembered stop, so a new request is never followed by a restart of the
     old one.
4. Phone: `StopsWithTheScreen` observes `ON_STOP` and `ON_START` (extracted from `PlayerScreen`,
   which the extra branch took over detekt's complexity limit). Television: the existing observer
   adds `ON_START`, and its dispose calls `onLeft()`.

## Files Touched

- `core/media/src/main/kotlin/dev/quiblo/core/media/PlayerController.kt`
- `core/media/src/main/kotlin/dev/quiblo/core/media/Media3PlayerController.kt`
- `feature/player/src/main/kotlin/dev/quiblo/feature/player/PlayerViewModel.kt`
- `feature/player/src/main/kotlin/dev/quiblo/feature/player/PlayerScreen.kt`
- `feature/player/src/test/kotlin/dev/quiblo/feature/player/PlayerReleasesLiveTest.kt` (new)
- `app-tv/src/main/kotlin/dev/quiblo/tv/ui/player/TvPlayerScreen.kt`
- `agile/items|plans|testing/BUG-037-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: returning to a live channel now takes a fresh start (one channel change's worth of
  buffering) instead of showing a frozen frame. That frame was of a broadcast that had moved on.
- Risk: `PlayerController`, its implementation and `PlayerViewModel` each crossed detekt's
  function-count threshold by one. Suppressed with the reason at each site: each is one function
  per thing a screen does to a playback session, and splitting them would give two apps two
  objects to keep in step.
- Rollback: revert the branch commit.
