# BUG-036 Automated Tests

## Tests written

| Test | Layer | Asserts |
| --- | --- | --- |
| `falling behind the live window rejoins the live edge` | unit | `BEHIND_LIVE_WINDOW` with no rejoins yet → rejoin |
| `rejoining is bounded, so a broken window still ends in an error` | unit | Two rejoins → rejoin; three → not |
| `nothing else is mistaken for falling behind` | unit | Bad status, timeout, malformed container, unspecified → never a rejoin |
| `a stream behind its window that cannot be rejoined is not called gone or refused` | unit | Exhausted rejoins fall through as `UNKNOWN`, which the mid-playback ladder retries |

The calls the controller makes on a rejoin — `seekToDefaultPosition()` then `prepare()` — need a
real engine and a real live window, and are covered by the sweep.

## Verified to fail without the fix

`rejoinsLiveEdge` reduced to never rejoin (the old behaviour: the error went straight to the retry
ladder) and `:core:media:testDebugUnitTest` re-run on 2026-10-03:

```
LiveEdgeTest > rejoining is bounded, so a broken window still ends in an error() FAILED
LiveEdgeTest > falling behind the live window rejoins the live edge() FAILED
23 tests completed, 2 failed
```

## Run result

`:core:media:testDebugUnitTest` — **PASSED** (23 tests) on branch
`bugfix/BUG-036-live-window-recovery` (local, 2026-10-03). `:core:media:detekt` and lint clean.
