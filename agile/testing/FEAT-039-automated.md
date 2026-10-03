# FEAT-039 Automated Tests

## Tests written

`ManageProfilesTest` — Robolectric + Compose, driving the phone's dialogs as a person would:

| Test | Layer | Asserts |
| --- | --- | --- |
| `everybody with a profile is listed, and the guest is not` | UI (Robolectric) | Named profiles shown; no "Guest" anywhere |
| `a profile can be deleted, after saying what goes with it` | UI (Robolectric) | Tap Alex → Delete this profile → "Delete Alex?" → Delete → `onDelete(Alex)` |
| `keeping a profile at the question deletes nothing` | UI (Robolectric) | Keep → no `onDelete` |
| `a profile can be renamed` | UI (Robolectric) | Replace the name, Save → `onRename(Sam, "Samira")` |

The television editor is not driven by a test here: the existing television UI tests drive
`aboutSection` and the settings fields, and a D-pad walk of the chooser in Robolectric would test
Robolectric's key dispatch more than this screen. It is in the sweep, step by step.

## Verified to fail without the fix

With the dialog's delete and rename no longer reaching their callbacks — which is the state of the
app before this branch, where nothing reached `ProfilesViewModel.delete` — `ManageProfilesTest`
re-run on 2026-10-03:

```
ManageProfilesTest > a profile can be deleted, after saying what goes with it FAILED
ManageProfilesTest > a profile can be renamed FAILED
4 tests completed, 2 failed
```

## Run result

`:feature:settings:testDebugUnitTest` and `:app-tv:testDebugUnitTest` — **PASSED** on branch
`feature/FEAT-039-manage-profiles` (local, 2026-10-03). Detekt clean on `:app`, `:app-tv`,
`:feature:settings`, `:feature:designsystem`; both apps compile.
