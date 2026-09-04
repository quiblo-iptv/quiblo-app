# BUG-033 Automated Tests

## Tests written

| Test | Layer | Asserts |
| --- | --- | --- |
| `a failed live category list fails the load` | unit | Live streams OK + `get_live_categories` 502 → `SourceResult.Failure` |
| `a failed film category list fails the load` | unit | Film streams OK + `get_vod_categories` 502 → `SourceResult.Failure` |
| `an empty film list needs no categories` | unit | No films + `get_vod_categories` 502 → still `Success`, live grouping intact |
| `loads live streams with categories` (existing) | unit | The happy path still groups as the provider intends |

Each asserts on the **load's outcome**, not on `groupTitle`. An assertion about the grouping could
be satisfied by a mapper that guesses a name; only refusing to report success protects the stored
catalogue, because `SourceRepository.store` writes nothing on a failure.

## Verified to fail without the fix

`XtreamSource.kt` was stashed and `:source:xtream:test` re-run on 2026-09-05:

```
XtreamSourceTest > BUG-033 — film categories that fail take the whole load with them FAILED
XtreamSourceTest > BUG-033 — live categories that fail take the whole load with them FAILED
```

`an empty film list needs no categories` passes with and without the fix, deliberately: it guards
against the fix being tightened into rejecting healthy live-only accounts.

## Run result

`:source:xtream:test` — **PASSED** on branch `bugfix/BUG-033-sync-erases-categories` (local,
2026-09-05).
