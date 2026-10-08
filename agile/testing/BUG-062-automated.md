# BUG-062 Automated Tests

## Tests written

`PlaybackDecisionsTest.WhenTheInitialLoadRunsOutOfTime` in `:core:media`:

| Test | Asserts |
| --- | --- |
| `a live channel fails at the budget, whatever arrived` | live → `GiveUp` |
| `a film that sent nothing fails at the budget, as AC-PLAY-05 asks` | 0 bytes → `GiveUp` |
| `a film still receiving data is waited for` | recent byte → `WaitMore(2 s)`, at 12 s and at 30 s |
| `the wait never runs past the limit` | 500 ms left → `WaitMore(500)`; at 40 s → `GiveUp` |
| `a film that sent data and went quiet is loaded once more` | 4 s quiet → `Reload` |
| `a second stall is the error` | quiet after one reload → `GiveUp` |
| `no reload is started without a full budget left for it` | less than 12 s left → `GiveUp` |
| `a film that keeps trickling is given up on at the limit` | the waits add up to exactly 40 s |

The watchdog loop, the stop before a reload and the stop before the error need an engine; they are
in the sweep.

## Run result

This container has no Android SDK, so `:core:media:testDebugUnitTest` could not run here. The
function and the eight tests above were compiled and run on their own in a plain Kotlin/JVM
project (2026-10-08): **8 PASSED**. CI runs the module.
