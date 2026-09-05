# FEAT-031 Automated Tests

## Tests written

| Test | Layer | Asserts |
| --- | --- | --- |
| `both jobs are registered, on their own intervals` (updated) | Robolectric | Catalogue on 4 hours, popular still on 40 |
| `the catalogue job takes the interval it is given` | Robolectric | A chosen interval reaches WorkManager |
| `the four-day job is cancelled, not left running beside the new one` | Robolectric | The legacy unique name ends up `CANCELLED` |
| `a deliberate change replaces the running schedule` | Robolectric | `UPDATE` on a viewer's change |
| `scheduling twice does not restart the interval` (existing) | Robolectric | `KEEP` on a launch |
| `a load carries its own fingerprint` | unit | A first load groups, and returns something to compare |
| `an unchanged account skips the grouping` | unit | Exactly `auth, live, films, series` — the three category calls are not spent |
| `a new film changes the fingerprint` | unit | An added `added` date is not mistaken for nothing |
| `no known fingerprint means the long way round` | unit | The manual path always does the full load |
| `a row that has not changed is not written` | Robolectric + SQLite | `total_changes()` moves by 1 — the delete only |
| `a row that has changed is still written` | Robolectric + SQLite | `total_changes()` moves by 1 — the changed row only |
| `24 to 25 adds a fingerprint that starts empty` | Robolectric + SQLite | Column added, existing rows null, catalogue intact |

## Verified to fail without the change

`Daos.kt` was stashed and `:core:database:testDebugUnitTest` re-run on 2026-09-05:

```
CatalogueMergeTest > a row that has changed is still written FAILED
CatalogueMergeTest > a row that has not changed is not written FAILED
```

The merge tests count SQLite's own `total_changes()` rather than comparing rows. Comparing the
stored row before and after would prove nothing: a row written back identically *is* identical
afterwards, which is exactly the case under test.

## One existing assertion changed, deliberately

`XtreamSourceTest > a refresh stops the moment the panel refuses, mid-catalogue` counted four
requests: auth, live streams, live categories, then the refusal. It now counts three. The
grouping is no longer fetched between the stream lists — that reordering is the whole saving, and
the test's subject (the walk stops at the first refusal) is unchanged.

## Run result

`./gradlew detekt test` — **PASSED** on branch `feature/FEAT-031-four-hourly-sync` (local,
2026-09-05).
