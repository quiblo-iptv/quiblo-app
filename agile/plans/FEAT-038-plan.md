# FEAT-038 Implementation Plan

## Approach

1. `ProfileDao`: `@Query("UPDATE profiles SET name = :name WHERE id = :id") suspend fun rename(id, name)`.
   A query, not a schema change — no migration.
2. `ProfileRepository.rename(profile, name)`: trim; read the row; refuse a blank name, a missing
   row and a guest row; otherwise update. The guest check reads the row rather than trusting the
   `Profile` passed in, which a screen may have held since before the session changed.
3. `ProfilesViewModel.rename(profile, name)` and `setAvatar(profile, avatar)`, launched on the
   ViewModel's scope like its other actions.
4. Nothing needs to tell the screens: `activeProfile` is `combine(storedId, observeAll())`, so the
   updated row flows to every screen showing the active profile's name.

## Files Touched

- `core/database/src/main/kotlin/dev/quiblo/core/database/dao/Daos.kt`
- `core/data/src/main/kotlin/dev/quiblo/core/data/ProfileRepository.kt`
- `core/data/src/test/kotlin/dev/quiblo/core/data/ProfileRepositoryTest.kt`
- `feature/settings/src/main/kotlin/dev/quiblo/feature/settings/ProfilesViewModel.kt`
- `agile/items|plans|testing/FEAT-038-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: none to data — one column of one row, by primary key.
- Rollback: revert the branch commit. Existing names are unaffected either way.
