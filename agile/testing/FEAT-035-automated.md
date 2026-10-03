# FEAT-035 Automated Tests

## Tests written

| Test class | Module | Asserts |
| --- | --- | --- |
| `PlaybackVerdictTest` (21 tests, nested by side) | `:core:data` | Every row of the audit's verdict table: offline outranks all; expired / disabled / rejected outrank the stream; connection limit needs *both* a full account and a refusal status; unreachable or 5xx panel → provider down; firewall → blocking; healthy account + 404/410 or timeout-with-no-data → channel; data + parse failure → format; data + unexpected engine error → Quiblo. The M3U column (401, unreachable, 404, everything else undetermined). The "no evidence → `UNDETERMINED`" cases: account unavailable, Quiblo never blamed for a stream that sent nothing or without an account check, a refusal with screens to spare. |
| `PlaybackDiagnoserTest` (8) | `:core:data` | Expired account named with its date; five failures inside a minute ask the panel once, a sixth after 61 s asks again; offline asks nobody; unanswered check → undetermined; a playlist is judged on the stream alone; a deleted source blames nobody; the details name the host and never the credentials; every diagnosis is logged newest first. |
| `DiagnosisDetailsTest` (6) | `:core:data` | `hostOf` drops an Xtream path, an M3U query and URL userinfo; non-URLs name no host; the details line never carries a path; a playlist says there was no account to check. |
| `XtreamAccountHealthTest` (13) | `:source:xtream` | Active account → `Ok` with expiry and screens; `active == max` is a limit, missing figures never are; `exp_date` in the past with `status = Active` → `Expired`; `auth = 0` and HTTP 401 → rejected; banned → disabled; 503 → server error; unknown host → unreachable; 462 → blocked and backoff begins; a panel already in backoff is not asked; no credentials → null; a **refresh** of an account past its date → `SubscriptionExpired`. |
| `XtreamDtoTest` — account status (+5) | `:source:xtream` | Expired by date while active; future date not expired; `"0"`, `0`, `""`, `null` mean never; `active_cons` read whatever its type; missing connection figures stay null. |
| `PlaybackEvidenceTest` (6) | `:feature:player` | The player's failure maps to the diagnosis's evidence (status wins; each error keeps its kind; bytes and had-played carried); **copied details never contain the credentialed path** or the channel name. |

The account check runs on `Dispatchers.Default` inside `runTest`: it is bounded by a timeout, and
`runTest`'s virtual clock fired that timeout before the mock engine had answered.

## Verified to fail without the fix

**Expiry by date.** `UserInfo.isExpiredAt` reduced to the old status-only check and
`:source:xtream:test` re-run on 2026-10-03:

```
XtreamAccountHealthTest > a refresh of an account past its expiry date reports the expiry() FAILED
XtreamAccountHealthTest > an expiry date in the past is expired though the panel still says active() FAILED
XtreamDtoTest > account status > an expiry date in the past is expired even while the status says active() FAILED
115 tests completed, 3 failed
```

**Redaction.** `hostOf` changed to return the whole URL and the diagnostics and player tests
re-run:

```
PlaybackEvidenceTest > copied details never contain the credentialed path() FAILED
DiagnosisDetailsTest > an xtream path loses its username and password() FAILED
DiagnosisDetailsTest > the details line never carries a path() FAILED
DiagnosisDetailsTest > userinfo in the authority is not part of the host() FAILED
DiagnosisDetailsTest > an m3u query loses its credentials() FAILED
PlaybackDiagnoserTest > the details name the host and never the credentials() FAILED
```

The verdict table is new behaviour with no previous implementation to fail against; before this
branch every failure produced the engine-level sentence and no verdict at all.

## Run result

On branch `feature/FEAT-035-whose-fault-it-is` (local, 2026-10-03): `detektAll` clean;
`:core:data`, `:core:media`, `:source:xtream`, `:feature:player`, `:feature:settings`, `:app` and
`:app-tv` unit tests **PASSED**.

`:core:database`'s `MigrationTest` fails on this Windows machine before and after the branch —
Room's test driver compares `migration-test.db` against a backslash path — and is untouched here.
CI runs on Linux.
