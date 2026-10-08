# BUG-062 Implementation Plan

## Approach

1. `afterLoadTimeout(isLive, bytesReceived, millisSinceLastByte, elapsedMillis, reloadsSoFar)` in
   `PlaybackDecisions.kt` → `WaitMore(ms)`, `Reload` or `GiveUp`; a plain function, tested as a
   table like `nextStep`. Limits: 40 s overall, 4 s without data is a stall, 2 s between looks, one
   reload, and a reload only with a full 12 s budget left.
2. `Media3PlayerController`: the transfer listener records when the last network byte arrived
   (`lastByteAtMillis`, beside `bytesReceived`); the watchdog loops on `afterLoadTimeout` after its
   first 12 s.
3. `reloadAfterStall()`: cancel a pending retry, `player.stop()`, count it in `retryAttempt`, and
   `restart()` at the same position.
4. `failOnTimeout()`: cancel a pending retry, `player.stop()`, then report — so the connection is
   closed and `retry()` finds an idle engine.

## Files Touched

- `core/media/.../PlaybackDecisions.kt`, `core/media/.../Media3PlayerController.kt`
- Tests: `PlaybackDecisionsTest`
- `agile/items|plans|testing/BUG-062-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: a film that will never play now takes up to 40 s to say so, where it took 12 s. Only when
  data is arriving or arrived; one that sent nothing still fails at 12 s.
- Risk: the reload opens a second request to the panel. The first is closed before it, which is
  what a one-screen panel needs to see.
- Rollback: revert the branch commit.
