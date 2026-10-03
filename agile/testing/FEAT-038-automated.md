# FEAT-038 Automated Tests

## Tests written

Added to `ProfileRepositoryTest`, whose mock `ProfileDao` now answers `rename` by rewriting the row
in its in-memory table:

| Test | Layer | Asserts |
| --- | --- | --- |
| `renaming trims the new name and keeps the same profile` | unit | `"  Mahmoud  "` → `"Mahmoud"`; same id |
| `a blank new name is refused and changes nothing` | unit | `false`; the old name stands |
| `a guest is never renamed` | unit | `false`; still "Guest" |
| `a profile that has gone is not renamed` | unit | `false` after delete |
| `the person watching sees their new name everywhere at once` | unit | `activeProfile` emits the new name |

## Verified to fail without the fix

`ProfileRepository.rename` reduced to a no-op returning false and `ProfileRepositoryTest` re-run on
2026-10-03:

```
ProfileRepositoryTest > FEAT-038 — a profile can be renamed, and keeps everything it had FAILED
ProfileRepositoryTest > the person watching sees their new name everywhere at once() FAILED
15 tests completed, 2 failed
```

The three refusal tests pass either way, by design: they pin what must *not* be renamed.

## Run result

`ProfileRepositoryTest` — **PASSED** (15) on branch `feature/FEAT-038-rename-profiles` (local,
2026-10-03). `:core:data:detekt` clean; `:feature:settings` compiles.
