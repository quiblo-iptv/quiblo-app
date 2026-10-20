# FEAT-040 Automated Tests

## Tests written

`PlaybackInfoFormatTest` in `:feature:player`:

| Test | Asserts |
| --- | --- |
| `bitrates read in the unit a person would say` | `18.4 Mb/s`, `640 kb/s`, `0 kb/s` |
| `sizes read in binary units` | `840 KB`, `512.0 MB`, `2.00 GB` |
| `times read as a clock, hours only when there are any` | `2:03`, `1:02:03`, `24.3 s` |
| `frame rates keep the decimals they have and no more` | `23.976`, `25`, `59.94` |
| `a film at a rate the screen cannot hold evenly is flagged` | 23.976/60, 25/60, 30/50 |
| `a film at a rate the screen holds evenly is not` | 25/50, 30/60, 23.976/24, 24/120 |
| `an unknown rate is never flagged` | a zero on either side |

The sampler reads a live engine and the overlay is Compose; both are in the sweep.

## Run result

This container has no Android SDK. `PlaybackInfoFormat.kt` and the seven tests were compiled and
run on their own in a plain Kotlin/JVM project (2026-10-20): **7 PASSED**. detekt 1.23.8 with the
repo config and its ktlint formatting rules over `core/media`, `feature/player` and the television
player: clean. CI runs the modules and Android Lint.
