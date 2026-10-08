# BUG-063 Automated Tests

| Test | Asserts |
| --- | --- |
| `PlaybackDecisionsTest` `an empty answer is a refusal, not a format` | both container codes, no data → `PROVIDER_REFUSED` |
| `PlaybackDecisionsTest` `an empty answer is asked again before the first frame` | → `RetryAfter(2 s, 1)` |
| `PlaybackVerdictTest` `every screen in use and nothing at all from the stream is the connection limit` | timeout or `OTHER`, no data, full → `CONNECTION_LIMIT` |
| `PlaybackVerdictTest` `every screen in use and a stream that sent data is not called a connection limit` | data received → `UNDETERMINED` |

The existing `a container nothing recognises is a format failure` still holds: data arrived.

## Run result

No Android SDK in this container. `PlaybackDecisions.kt` (against a stub of Media3's error-code
constants), `PlaybackVerdict.kt`, `AccountHealth.kt` and both full test classes were compiled and
run in a plain Kotlin/JVM project (2026-10-08): **52 PASSED**. CI runs the modules.
