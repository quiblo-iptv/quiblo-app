# BUG-034 Implementation Plan

## Approach

1. New `PlaybackDecisions.kt` in `:core:media`, all `internal`:
   - `EngineFailure(errorCode, httpStatus, hostUnreachable)` — the facts a decision reads.
   - `classify(EngineFailure): PlaybackError` — the old `toPlaybackError` table plus the status
     split, the unreachable split and `PARSING_CONTAINER_UNSUPPORTED`.
   - `nextStep(error, hasEverBeenReady, retriesSoFar, elapsedMillis): NextStep` — `RetryAfter`
     or `GiveUp`. Mid-playback rules are unchanged (three attempts, 1.5 s × n). Before the first
     frame, `PROVIDER_REFUSED` and `TIMEOUT` get two attempts at 2 s × n, and only when
     `elapsed + delay < INITIAL_LOAD_TIMEOUT_MILLIS`.
   - The retry and budget constants move here so the function and the watchdog read one value.
2. `Media3PlayerController`:
   - `PlaybackException.toEngineFailure()` walks the cause chain (bounded) for
     `InvalidResponseCodeException.responseCode` and for `UnknownHostException`,
     `ConnectException`, `NoRouteToHostException`. It is the only code that touches engine types.
   - `scheduleRetry` asks `nextStep` and does what it says.
   - `lastFailure` is kept through a retry so a watchdog that fires mid-retry reports it.
   - `retry()` restarts the elapsed clock, since a manual retry has a fresh budget.
3. `PlaybackError` gains `AUTH_REJECTED` and `PROVIDER_REFUSED`; `PlaybackErrorText` and
   `strings.xml` gain interim wording for both. `player_reconnecting` drops "of 3", which is
   wrong for an initial load that gets two.

## Files Touched

- `core/media/src/main/kotlin/dev/quiblo/core/media/PlaybackDecisions.kt` (new)
- `core/media/src/main/kotlin/dev/quiblo/core/media/Media3PlayerController.kt`
- `core/media/src/main/kotlin/dev/quiblo/core/media/PlayerController.kt`
- `core/media/src/test/kotlin/dev/quiblo/core/media/PlaybackDecisionsTest.kt` (new — first test in the module)
- `feature/player/src/main/kotlin/dev/quiblo/feature/player/PlaybackErrorText.kt`
- `feature/player/src/main/res/values/strings.xml`
- `agile/items|plans|testing/BUG-034-*`
- `CHANGELOG.md`

## Risks & Rollback

- Risk: a retried refusal costs the panel two more requests on an account that is genuinely at
  its limit. Mitigation: two attempts, spaced 2 s and 4 s, inside a 12 s budget — and the
  connection slot not yet being freed is by far the commoner case.
- Risk: an error now appears up to ~6 s later than before for a refused stream. It still lands
  inside `AC-PLAY-05`; the screen says "Reconnecting… (attempt n)" meanwhile.
- Rollback: revert the branch commit. Behaviour returns to one attempt and "no longer available".
