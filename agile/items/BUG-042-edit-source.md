# BUG-042: a source's server address or credentials cannot be changed

Audit item **SEC-2**.

## Problem & Motivation

IPTV providers change DNS names and passwords routinely. The only way to give Quiblo a new address
or password was to delete the source and add it again — and deleting a source cascades away every
favourite and resume point it had. A household paid for every change on the provider's side with
its history.

Until it did, every stream request went to the old host with the old password, and failed with a
401 or 403 that `BUG-034` and `FEAT-035` now at least describe correctly.

## Environment

- Platform: both
- Source kind: both; most often Xtream (password, DNS), sometimes M3U (the playlist URL moves)

## Root cause

`SourceRepository` had `addSource`, `refresh` and `deleteSource`, and nothing that changed a source.
Neither app's sources screen offered an edit.

## Scope

- `SourceRepository.editSource(id, name, url, username, password)`: same id; the change is written,
  the source refreshed against the panel, and on failure the previous name, address and
  credentials are restored
- Empty password keeps the stored one; the current password is never read back into a form
- `SourceRepository.username(id)` to fill the form; `SourcesViewModel.editSource` / `usernameOf`
- An Edit action on the sources screen of both apps
- With `BUG-041` in place no stored row carries the old host or password, so nothing else is
  rewritten

## Explicit Non-Scope

- Changing a source's kind (playlist ↔ account). That is a different source; delete and add.
- Showing the account's expiry and screens on the source — the `FEAT-035` nice-to-have, still open
- Re-pointing an M3U picked from a file to another file through the system picker; the address can
  be edited as text

## Acceptance Criteria

- Editing a source's address and password keeps its favourites and resume points
- A change the provider refuses leaves the source exactly as it was, and says it failed
- An empty password field keeps the current password
- The current password is never displayed
