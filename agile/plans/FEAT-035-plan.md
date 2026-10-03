# FEAT-035 Implementation Plan

## Approach

1. **`:source:api`** — `AccountHealth` (`Ok(expiresAt, active, max)`, `Expired(at)`, `Disabled`,
   `CredentialsRejected`, `Unreachable`, `ServerError(code)`, `Blocked`).
   `MediaSource.accountHealth(request): AccountHealth?` defaults to null and
   `MediaSource.checksAccount` to false, so a playlist is "no account to ask" rather than "the
   question went unanswered" — the two lead to different verdicts.
2. **`:source:xtream`** — `UserInfo` gains `active_cons`, `expiresAtEpochMillis` (zero is
   "never") and `isExpiredAt(now)`. `authorised()` uses it, so a refresh of an account past its
   date reports `SubscriptionExpired`. `accountHealth()` is one `authenticate` call under
   `withTimeoutOrNull(5 s)`, gated by `isBlocked()`, mapped field by field; anything that is not
   an answer is null.
3. **`:core:media`** — `FailureDetails` on `PlaybackState`, filled whenever the status becomes
   `ERROR`. A `TransferListener` on the data source counts network bytes for the current item;
   `onPlayerError` keeps the `EngineFailure` and `errorCodeName`.
4. **`:core:data`, package `diagnostics`**
   - `verdict(deviceOnline, AccountEvidence, StreamEvidence): Verdict` — pure, the table from the
     audit. `AccountEvidence` is `NotApplicable` / `Unavailable` / `Answered(health)`.
   - `hostOf(url)` and `detailsLine(...)` — the only text a diagnosis carries about a URL is its
     host; the line is built from numbers, enum names and that host.
   - `PlaybackDiagnoser` — connectivity first (offline asks nobody), then the account (mutex-held
     60 s cache per source), then `verdict`. Every result goes to `PlaybackLog`.
   - `PlaybackLog` — a 20-entry `StateFlow` ring, newest first, memory only.
5. **`:feature:player`** — `PlaybackState.streamEvidence()` translates the player's vocabulary
   into the diagnosis's. `PlayerViewModel` diagnoses when status becomes `ERROR` and forgets when
   it stops being one. `VerdictText.kt` holds the side labels, icons, headlines (with the expiry
   date or the screens when known) and advice. Both error screens show the engine message plus
   "Checking why…" at once, then the verdict in place, then the details line; the phone adds
   **Copy details**.
6. **Settings** — `PlaybackLogViewModel`; a card on the phone's App tab with **Copy all details**;
   focusable rows under App on the television. `:feature:settings` now depends on
   `:feature:player` for the wording, so there is one set of sentences to translate.

## Files Touched

- `source/api/.../AccountHealth.kt` (new), `MediaSource.kt`
- `source/xtream/.../XtreamSource.kt`, `dto/XtreamDto.kt`; tests `XtreamAccountHealthTest.kt` (new), `XtreamDtoTest.kt`
- `core/media/.../PlayerController.kt`, `Media3PlayerController.kt`
- `core/data/.../diagnostics/{PlaybackVerdict,PlaybackDiagnoser,DiagnosisDetails,PlaybackLog}.kt` (new), `di/DataModule.kt`;
  tests `diagnostics/{PlaybackVerdictTest,PlaybackDiagnoserTest,DiagnosisDetailsTest}.kt` (new)
- `feature/player/.../{DiagnosisState,VerdictText}.kt` (new), `PlayerViewModel.kt`, `PlayerScreen.kt`, `res/values/strings.xml`;
  tests `PlaybackEvidenceTest.kt` (new), `PlayerResumeWriteTest.kt`
- `feature/settings/.../{PlaybackLogViewModel,PlaybackLogCard}.kt` (new), `SettingsScreen.kt`, `di/`, `build.gradle.kts`, `res/values/strings.xml`
- `app-tv/.../player/TvPlayerScreen.kt`, `settings/TvSettingsScreen.kt`, `res/values/strings.xml`
- `agile/items|plans|testing/FEAT-035-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: a wrong verdict sends a viewer to the wrong party. Mitigation: the honesty rule is the
  shape of `verdict()` — every branch that names a side needs an answer from the account check
  (or, for a playlist, an unambiguous stream status), and the tests pin each "no evidence →
  undetermined" case.
- Risk: the account check adds load on the panel. One call, rate-limited, cached 60 s per source,
  skipped offline and while the panel's block backoff is running.
- Risk: a device with a wrong clock reads a valid account as expired. Mitigation: only an
  `exp_date` *before* now counts, and a television whose clock is in the past — the common fault
  on boxes without a battery-backed clock — reads it as not yet expired.
- Rollback: revert the branch commit. The error screens return to the engine-level sentence.
