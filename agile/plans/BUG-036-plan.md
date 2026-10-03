# BUG-036 Implementation Plan

## Approach

1. `rejoinsLiveEdge(failure, rejoinsSoFar)` in `PlaybackDecisions.kt` — true for
   `ERROR_CODE_BEHIND_LIVE_WINDOW` while fewer than `MAX_LIVE_EDGE_REJOINS` (3) rejoins have been
   made since the item last reached `STATE_READY`.
2. `Media3PlayerController.onPlayerError` asks it first. When it says yes: count the rejoin,
   `seekToDefaultPosition()`, `prepare()`, and return — no `scheduleRetry`, no retry attempt, no
   change to what the screen shows. The counter resets on `prepare`, `retry` and `STATE_READY`.
3. `restart()` replaces the two bare `player.prepare()` calls in `retry()` and in the scheduled
   retry: for a live item it seeks to the default position first.
4. `retry()` and a scheduled retry set `failure = null`, so a stale `FailureDetails` does not
   outlive the error it described.

## Files Touched

- `core/media/src/main/kotlin/dev/quiblo/core/media/PlaybackDecisions.kt`
- `core/media/src/main/kotlin/dev/quiblo/core/media/Media3PlayerController.kt`
- `core/media/src/test/kotlin/dev/quiblo/core/media/LiveEdgeTest.kt` (new)
- `agile/items|plans|testing/BUG-036-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: a viewer who paused a live channel expecting to resume where they were now jumps to live.
  There is no timeshift in v1 (`docs/FREEZE.md` §2); the old position was unplayable anyway.
- Risk: a broken server makes the player rejoin repeatedly. Bounded at three between plays, after
  which the ordinary retry ladder and error apply.
- Rollback: revert the branch commit.
