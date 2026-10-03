# BUG-040 Automated Tests

## Tests written

| Test | Module | Asserts |
| --- | --- | --- |
| `deleting a profile clears its remembered rows, its opinions and its settings` | `:core:data` | `clearForProfile(sam)` on both DAOs, Sam's preferences cleared; nothing of Alex's |
| `leaving a guest session clears what it said and chose` | `:core:data` | Sign-out from a guest clears its opinions, rows and preferences |
| `a guest nobody left is cleared at the next startup, settings and all` | `:core:data` | `beginSession()` with a guest left over does the same |
| `startup clears settings left by profiles that no longer exist, and keeps everyone else's` | `:core:data` | The sweep keeps exactly the living ids plus `NONE_ID` |
| `the owner of a key is the number after its last at sign` | `:core:datastore` | `@3` → 3, `@13` → 13, unscoped and non-numeric → null |
| `clearing one profile removes every key it wrote, of every type` | `:core:datastore` | String, set and boolean keys of profile 3 go; unscoped keys stay |
| `profile 3 is not profile 13 or 31` | `:core:datastore` | Suffix matching is by id, not by text |
| `clearing everyone but the living removes what deleted profiles left` | `:core:datastore` | The startup sweep's predicate |

The `:core:datastore` tests build real `MutablePreferences` with `mutablePreferencesOf`, so the
removal is tested against DataStore's own key type rather than a stand-in.

## Verified to fail without the fix

`ProfileLeftovers` reduced to doing nothing and `ProfileRepositoryTest` re-run on 2026-10-03:

```
ProfileRepositoryTest > a guest nobody left is cleared at the next startup, settings and all() FAILED
ProfileRepositoryTest > BUG-040 — a guest's opinions and settings end with the session FAILED
ProfileRepositoryTest > startup clears settings left by profiles that no longer exist, and keeps everyone else's() FAILED
ProfileRepositoryTest > BUG-040 — deleting a profile clears what no foreign key reaches FAILED
19 tests completed, 4 failed
```

## Run result

`:core:data:testDebugUnitTest` and `:core:datastore:testDebugUnitTest` — **PASSED** on branch
`bugfix/BUG-040-profile-data-left-behind` (local, 2026-10-03). Detekt clean; both apps compile.
