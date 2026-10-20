# FEAT-041 Automated Tests

`FrameRateMatchingTest` in `:feature:player`:

| Test | Asserts |
| --- | --- |
| `a 23_976 film moves a 60 Hz panel to 23_976 Hz` | the exact mode wins |
| `without an exact mode, 24 Hz will do for 23_976` | the nearest fit |
| `a 25 fps film prefers 50 Hz to 25 Hz` | the higher of two even fits |
| `a rate the panel already shows evenly is left alone` | 30 on 60, 60 on 60, 25 on 50 |
| `a panel with nothing that fits is left alone` | no 24/23.976/48 → null |
| `resolution is never given up for an even cadence` | a 1080p 24 Hz mode is not taken from 4K |
| `an unknown frame rate switches nothing` | 0 → null |

`MatchFrameRateSettingTest` in `:feature:settings`: the switch reads as on before the store answers,
and switching it off is written through — the same pair the ambient switch is held to.

## Run result

No Android SDK in this container. `matchingDisplayMode` and its seven tests, with `PlaybackInfoFormat`
and its seven, were compiled and run on their own in a plain Kotlin/JVM project (2026-10-20):
**14 PASSED**. detekt 1.23.8 with the repo config and its ktlint rules over every module touched:
clean. The settings test, the window switch and Lint run in CI.
