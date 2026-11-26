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

`DecoderChoiceTest` in `:core:media`:

| Test | Asserts |
| --- | --- |
| `HEVC is hardware only when the box has a hardware decoder` | software dropped from `[hw, sw]` |
| `every hardware-only codec is treated the same` | HEVC, VP9, AV1, Dolby Vision |
| `software is let in once allowed` | `[hw, sw]` kept |
| `a box with no hardware decoder keeps the software one` | `[sw]` kept |
| `H264 and audio keep their fallback` | lists untouched |
| `the hardware decoder is tried again first` | init failure → `RetryHardware(1 s)` |
| `then software is let in` | second init failure → `AllowSoftware` |
| `a failure with software already allowed is an ordinary failure` | → `NotMine` |
| `any other failure is not this decision's` | a bad status → `NotMine` |

The frame watch loop, the stop-then-prepare and the decoder selector need an engine; they are in
the sweep.

## Run result

This container has no Android SDK, so `:core:media:testDebugUnitTest` could not run here. The two
functions and the six tests above were compiled and run on their own in a plain Kotlin/JVM project
(2026-10-20): **6 PASSED**. `DecoderChoice.kt` and its nine tests the same way, with the two
Media3 constant classes stubbed at 1.10.1's values: **9 PASSED**. detekt 1.23.8 over `core/media/src` (without the ktlint ruleset): clean.
CI runs the module.
