# BUG-063 Automated Tests

## Tests written

`PlaybackDecisionsTest.WhenAFilmDropsFrames` in `:core:media`:

| Test | Asserts |
| --- | --- |
| `a quarter of the frames dropped is stutter` | 12 of 48 dropped, 38 of 48 dropped → stutter |
| `a few dropped frames are not` | 2 of 48, 0 of 48 → not |
| `a window with too few frames says nothing` | fewer than 12 frames → not |
| `a moment of stutter is not reloaded` | two windows → no reload |
| `sustained stutter is reloaded` | three windows → reload, for the first and second time |
| `a film that stutters through every reload is left to play` | after two reloads → no reload |

The frame watch loop and the stop-then-prepare need an engine; they are in the sweep.

## Run result

This container has no Android SDK, so `:core:media:testDebugUnitTest` could not run here. The two
functions and the six tests above were compiled and run on their own in a plain Kotlin/JVM project
(2026-10-20): **6 PASSED**. detekt 1.23.8 over `core/media/src` (without the ktlint ruleset): clean.
CI runs the module.
