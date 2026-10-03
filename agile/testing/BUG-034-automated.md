# BUG-034 Automated Tests

## Tests written

`PlaybackDecisionsTest` — the first test class in `:core:media`, JUnit 5 like every other module.

| Test | Layer | Asserts |
| --- | --- | --- |
| `404 and 410 are the only statuses that say a stream is gone` | unit | `classify` → `SOURCE_GONE` for those two only |
| `401 is a rejected account, not a missing stream` | unit | 401 → `AUTH_REJECTED` |
| `connection-limit and anti-flood answers are a refusal` | unit | 403, 429, 458, 460–463, 469, 509 → `PROVIDER_REFUSED` |
| `every server error is a refusal` | unit | 5xx → `PROVIDER_REFUSED` |
| `a status nobody named is not guessed at` | unit | 400, 418, missing status → `UNKNOWN` |
| `an unknown host or a refused connection is unreachable` | unit | connection failed + unreachable cause → `UNREACHABLE` |
| `a connection that opened and died is a timeout` | unit | connection failed / timed out → `TIMEOUT` |
| `a container nothing recognises is a format failure` | unit | `PARSING_CONTAINER_UNSUPPORTED` → `UNSUPPORTED_FORMAT` |
| `a missing local file is gone`, `drm is refused plainly` | unit | Unchanged mappings still hold |
| `a refusal is asked again after two seconds, then four` | unit | Before first frame: `RetryAfter(2000, 1)`, `RetryAfter(4000, 2)` |
| `a refusal is given up on after two retries` | unit | Third failure → `GiveUp` |
| `a timeout is asked again too` | unit | `TIMEOUT` before first frame is retried |
| `no retry is started that the watchdog would cut short` | unit | A wait that would end at or past 12 s → `GiveUp` |
| `every retry ends inside the AC-PLAY-05 budget` | unit | Walking the whole ladder ends before 12 s |
| `terminal errors and everything else are reported at once` | unit | Before first frame, nothing else is retried |
| `a drop gets three backed-off attempts, as AC-PLAY-06 asks` | unit | Mid-playback ladder unchanged: 1.5 s, 3 s, 4.5 s, then give up |
| `a refusal mid-playback is retried, where it used to be final` | unit | `PROVIDER_REFUSED` after playing → retried |
| `a stream that is gone, or an account that is rejected, is not retried` | unit | `SOURCE_GONE`, `AUTH_REJECTED` terminal |

`toEngineFailure()` itself — reading the cause chain — is not unit-tested: building a
`PlaybackException` or an `InvalidResponseCodeException` needs Android's `SystemClock` and `Uri`.
It is a dozen lines with no branches beyond `filterIsInstance` and `any`, and the sweep covers it.

## Verified to fail without the fix

The old behaviour was written into the same two functions — every status → `SOURCE_GONE`, no
unreachable split, `PARSING_CONTAINER_UNSUPPORTED` unmapped, `PROVIDER_REFUSED` terminal, nothing
retried before the first frame — and `:core:media:testDebugUnitTest` re-run on 2026-10-03:

```
PlaybackDecisionsTest > BeforeTheFirstFrame > a timeout is asked again too() FAILED
PlaybackDecisionsTest > BeforeTheFirstFrame > a refusal is asked again after two seconds, then four() FAILED
PlaybackDecisionsTest > AfterTheStreamHasPlayed > a refusal mid-playback is retried, where it used to be final() FAILED
PlaybackDecisionsTest > WhatTheTransportSays > a container nothing recognises is a format failure() FAILED
PlaybackDecisionsTest > WhatTheTransportSays > an unknown host or a refused connection is unreachable() FAILED
PlaybackDecisionsTest > WhatAStatusSays > 401 is a rejected account, not a missing stream() FAILED
PlaybackDecisionsTest > WhatAStatusSays > a status nobody named is not guessed at() FAILED
PlaybackDecisionsTest > WhatAStatusSays > connection-limit and anti-flood answers are a refusal() FAILED
PlaybackDecisionsTest > WhatAStatusSays > every server error is a refusal() FAILED
19 tests completed, 9 failed
```

The other ten pass either way, deliberately: they pin the behaviour that must *not* change — 404
is still final, the mid-playback ladder is still AC-PLAY-06's, and no retry outlives the budget.

## Run result

`:core:media:testDebugUnitTest` — **PASSED** (19 tests) on branch
`bugfix/BUG-034-provider-refusals-are-retried` (local, 2026-10-03). `detektAll` clean;
`:app` and `:app-tv` compile.
