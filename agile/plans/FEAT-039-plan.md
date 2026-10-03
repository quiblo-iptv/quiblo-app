# FEAT-039 Implementation Plan

## Approach

1. **Shared face picker.** `ProfileGate`'s private `AvatarPicker` moves, unchanged, to
   `:feature:designsystem` as `AvatarFacePicker`.
2. **Phone dialogs** in `:feature:settings`, `ManageProfiles.kt`:
   - `ManageProfilesDialog(profiles, onRename, onSetAvatar, onDelete, onDismiss)` — the named
     profiles as list items; tapping one opens the editor.
   - `EditProfileDialog(profile, onSave, onDelete, onDismiss)` — name field, face picker,
     **Delete this profile**; delete swaps to a confirmation naming the profile and what goes with
     it. Public, because the chooser in `:app` opens it too.
   - `onSave` renames only when the trimmed name differs and re-faces only when the face differs,
     so saving an untouched dialog writes nothing.
3. **Phone wiring.** `ProfileCard` gains **Manage profiles** (only when a named profile exists);
   `SettingsScreen` shows the dialog. `ProfileGate` replaces the card's click with
   `combinedClickable` — tap selects, long press edits — with a TalkBack label for the long press.
4. **Television editor** `TvEditProfile` in `TvProfileScreen.kt`: the add screen's layout — name,
   generated faces, then Save / Delete / Cancel. No face is chosen until one is pressed, so Save
   keeps the old one. Delete becomes an in-place question with **Keep** focused, and Back answers
   it with Keep.
5. **Television wiring.** `ProfileTile` takes `onLongClick` (via `combinedClickable`) and consumes
   Menu on key-up; the chooser shows the hint line when there is somebody to edit. Settings gains
   `manageProfiles(...)`: an expand row, then one row per named profile whose **Edit** turns that
   row into the editor.

## Files Touched

- `feature/designsystem/.../AvatarFacePicker.kt` (new)
- `feature/settings/.../ManageProfiles.kt` (new), `ProfileCard.kt`, `SettingsScreen.kt`, `res/values/strings.xml`
- `feature/settings/src/test/.../ManageProfilesTest.kt` (new)
- `app/.../player/ui/ProfileGate.kt`, `app/src/main/res/values/strings.xml`
- `app-tv/.../profiles/TvProfileScreen.kt`, `settings/TvSettingsScreen.kt`, `res/values/strings.xml`
- `agile/items|plans|testing/FEAT-039-*`, `CHANGELOG.md`

## Risks & Rollback

- Risk: deleting the wrong profile. Mitigation: two steps, the second naming the profile, with the
  safe answer focused on the television.
- Risk: a long press of OK is not delivered on some television remotes. Mitigation: Menu as well,
  and Settings → Manage profiles as the path that needs neither.
- Rollback: revert the branch commit. Nothing is stored differently.
