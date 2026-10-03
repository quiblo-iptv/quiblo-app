# BUG-040 Implementation Plan

## Approach

1. **DAOs** (queries only, no schema change): `FeedRowDao.clearForProfile(profileId)`,
   `TitleOpinionDao.clearForProfile(profileId)`, `ProfileDao.guestIds()` and `ProfileDao.allIds()`.
2. **DataStore.** In `ProfileScopedPreferences.kt`: `scopedOwner(keyName)` reads the id after the
   last `@` (null for an unscoped key), and `MutablePreferences.removeScoped(owns)` removes every key
   whose owner matches. `ProfileScopedStore` is implemented by `PlayerSettingsStore` and
   `ChannelLogoStore` — the two stores that use `Scoped` keys.
3. **`ProfileLeftovers`** in `:core:data` holds the two DAOs and the list of scoped stores:
   `clearRows(id)`, `clearPreferences(id)`, `clearPreferencesOtherThan(living)`.
4. **`ProfileRepository`** takes `ProfileLeftovers` and a `TransactionRunner`:
   - `delete` — rows and the profile row in one transaction, preferences after.
   - `endGuestSessions` — selects the guest ids, clears their rows and deletes them in one
     transaction, then clears their preferences. `signOut` and `startGuestSession` use it instead of
     calling `deleteGuests()` directly.
   - `beginSession` — after ending guests, clears preferences of every id not in `allIds()` plus
     `Profile.NONE_ID`.
5. **Koin**: `ProfileLeftovers` with `listOf(get<PlayerSettingsStore>(), get<ChannelLogoStore>())`,
   and the comment that a new scoped store belongs in that list.

## Files Touched

- `core/database/.../dao/Daos.kt`
- `core/datastore/.../ProfileScopedPreferences.kt`, `PlayerSettingsStore.kt`, `ChannelLogoStore.kt`
- `core/datastore/src/test/.../ProfileScopedPreferencesTest.kt` (new)
- `core/data/.../ProfileLeftovers.kt` (new), `ProfileRepository.kt`, `di/DataModule.kt`
- `core/data/src/test/.../ProfileRepositoryTest.kt`, `FeedRowCacheTest.kt`, `TitleOpinionRepositoryTest.kt` (fakes)
- `agile/items|plans|testing/BUG-040-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: the startup sweep removes a key that is not a profile's. Only keys whose text after the last
  `@` is a number are considered, and the app's own names contain no `@` (see `Scoped`); unscoped
  pre-profile values are never touched, and the test pins `@3` against `@13` and `@31`.
- Risk: a store added later with scoped keys and not listed in `ProfileLeftovers`. The comment at
  the Koin definition says where it belongs.
- Rollback: revert the branch commit. Orphans accumulate again; nothing else changes.
