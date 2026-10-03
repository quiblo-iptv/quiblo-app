# FEAT-038: a profile can be renamed, and its face changed after it is made

Audit item **PR-2**.

## Problem & Motivation

A profile's name could not be changed at any layer, and its face could only be chosen once, at
creation. A typo made on a remote, or a child's profile that has grown up, could only be fixed by
deleting the profile and making it again — which deletes its favourites, resume points and
settings with it.

`ProfileRepository`'s KDoc said the name was deliberately not editable. **The owner has reversed
that decision**: the id is the identity, the name is a label on it.

## Environment

- Platform: both
- Source kind: n/a
- Reported as "profiles cannot be renamed" alongside `FEAT-039` (they cannot be deleted either)

## Root cause

- No `UPDATE profiles SET name` anywhere: not in `ProfileDao`, `ProfileRepository` or
  `ProfilesViewModel`.
- `ProfileRepository.setAvatar` existed but `ProfilesViewModel` did not expose it.

## Scope

- `ProfileDao.rename(id, name)`
- `ProfileRepository.rename(profile, name): Boolean` — trimmed, never blank (the rule `addProfile`
  already has), never a guest, never a profile that has gone
- `ProfilesViewModel.rename()` and `setAvatar()`
- The KDoc that said the name was fixed, rewritten

## Explicit Non-Scope

- The screens that call these — `FEAT-039` (PR-1), which adds **Manage profiles** with Rename,
  Change face and Delete on both apps
- Unique names. Two profiles called "Sam" is a household's business; the chooser shows faces too.

## Acceptance Criteria

- Renaming trims the name and keeps the same profile id, so nothing keyed to it moves
- A blank name is refused and changes nothing
- A guest cannot be renamed
- The person watching sees their new name wherever the active profile's name is shown, without a
  restart
