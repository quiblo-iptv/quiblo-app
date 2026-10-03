# FEAT-039: profiles can be renamed, re-faced and deleted from a screen

Audit item **PR-1**.

## Problem & Motivation

The household reported that profiles cannot be deleted or renamed. Deleting was implemented at
every layer — `ProfileDao.delete`, `ProfileRepository.delete`, `ProfilesViewModel.delete` — and
correct, but no composable on either app called it, so `AC-PROF-06` could not be met by anybody
holding a remote. `FEAT-038` added renaming and changing a face below the screens; this adds the
screens.

## Environment

- Platform: both (`:app`, `:app-tv`)
- Source kind: n/a
- Where a viewer looks: the profile section of Settings, and the profile's own tile on the chooser

## Root cause

The profile card in Settings offered only **Switch**. The chooser offered select, add and guest.
Nothing anywhere led to delete.

## Scope

- Phone: **Manage profiles** beside **Switch profile** in Settings → Profile; a dialog listing the
  named profiles; each opens an editor with the name, the face picker and **Delete this profile**,
  which asks first and says what goes with it. A long press on a profile in the chooser opens the
  same editor.
- Television: **Manage profiles** in Settings → Profile, expanded into the list; each profile's
  **Edit** opens the editor in place. On the chooser, a long press of OK — or Menu, on remotes that
  have one — opens the same editor, and a line under the tiles says so.
- Guest is listed nowhere and offered nothing: it is a session, and it ends by leaving.
- The phone's face picker moves to `:feature:designsystem` as `AvatarFacePicker`, so the chooser
  and Settings offer the same faces.

## Explicit Non-Scope

- What deleting actually removes beyond the foreign-key cascade — `BUG-040` (PR-3) clears the
  opinions, remembered rows and per-profile settings the cascade cannot reach
- Protecting a profile from deletion (a PIN) — parked in `docs/PLAN.md` §6

## Acceptance Criteria

- From Settings on both apps, a named profile can be renamed, given a new face, and deleted
- Delete asks first, names the profile and says its favourites, continue watching and settings go
  with it; Keep deletes nothing
- On the chooser, a long press (phone) or long OK / Menu (television) on a profile opens the same
- Deleting the active profile returns to the chooser
- Guest appears in none of these
