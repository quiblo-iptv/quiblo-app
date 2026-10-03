# BUG-037 Automated Tests

## Tests written

`PlayerReleasesLiveTest`, against a relaxed mock `PlayerController`:

| Test | Layer | Asserts |
| --- | --- | --- |
| `a live channel sent to the background lets go of its connection` | unit | `onStopped()` on live → `stop()`, never `pause()` |
| `a film sent to the background is paused, and keeps its place` | unit | `onStopped()` on VOD → `pause()`, never `stop()` |
| `coming back to a live channel starts it again` | unit | `onStopped()` then `onStarted()` → one `retry()` |
| `coming back does nothing when nothing was let go` | unit | `onStarted()` after a paused film, and a second `onStarted()` → no `retry()` |
| `leaving the player lets go of a live channel and lets the same channel load again` | unit | `onLeft()` → `stop()`; loading the same channel again → `prepare()`; `onStarted()` → no `retry()` |

The controller's own half — `player.stop()`, and stopping the old live item before the new one is
set — needs an engine and a panel that counts connections, and is in the sweep.

## Verified to fail without the fix

`PlayerViewModel` made to treat every item as not live (the old pause-only behaviour) and
`:feature:player:testDebugUnitTest` re-run on 2026-10-03:

```
PlayerReleasesLiveTest > a live channel sent to the background lets go of its connection() FAILED
PlayerReleasesLiveTest > leaving the player lets go of a live channel and lets the same channel load again() FAILED
PlayerReleasesLiveTest > coming back to a live channel starts it again() FAILED
40 tests completed, 3 failed
```

## Run result

`:feature:player:testDebugUnitTest` (40) and `:core:media:testDebugUnitTest` — **PASSED** on branch
`bugfix/BUG-037-release-live-connection-when-stopped` (local, 2026-10-03). Detekt clean; `:app`
and `:app-tv` compile.
