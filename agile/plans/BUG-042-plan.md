# BUG-042 Implementation Plan

## Approach

1. `SourceRepository.editSource(sourceId, name, url, username?, password?)`:
   - Read the row and the stored credentials.
   - For an Xtream source, the new credentials are the given username (or the stored one when
     blank) and the given password (or the stored one when empty). No credentials at all →
     `Unauthorized` before anything is written.
   - `sourceDao.update` with the trimmed name (unchanged when empty) and URL; `credentialStore.put`.
   - `refresh(sourceId)`. On `Failure`, `sourceDao.update(before)` and the previous credentials are
     put back, so the source is exactly as it was.
2. `SourceRepository.username(sourceId)` — never the password.
3. `SourcesViewModel.editSource(source, name, url, username, password)` reports through the same
   `AddSourceState` as an add, so both screens' existing progress and result handling apply.
   `usernameOf(sourceId)` fills the form.
4. Phone: an Edit icon on each source row; `EditSourceDialog` with name, URL and for an account the
   username and a password field whose placeholder says empty keeps it.
5. Television: an Edit button beside Refresh and Remove; `TvEditSourceForm` in place of the list
   until saved or cancelled, with Back meaning Cancel.

## Files Touched

- `core/data/.../SourceRepository.kt`; test `SourceEditTest.kt` (new)
- `feature/sources/.../SourcesViewModel.kt`, `SourcesScreen.kt`, `res/values/strings.xml`
- `app-tv/.../sources/TvSourcesScreen.kt`, `TvAddSourceForm.kt`, `res/values/strings.xml`
- `agile/items|plans|testing/BUG-042-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: a panel that is merely slow or rate-limiting at the moment of saving makes a correct change
  look wrong, and it is rolled back. The viewer is told it failed and can save again; rolling back a
  correct change is recoverable, keeping a wrong one breaks a working source.
- Risk: the rollback itself does not run if the process dies mid-refresh. The new values stay, and
  the source shows as failing until edited again — no worse than today's delete-and-re-add.
- Rollback: revert the branch commit.
