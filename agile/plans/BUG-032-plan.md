# BUG-032 Implementation Plan

## Approach

1. Extract `tvPlayerBackAction(state)` / `tvPlayerBackConsumesKey(action)` beside the
   shell’s `tvBackAction` pattern so the hierarchy is unit-testable.
2. Wire `BackHandler` and `onPreviewKeyEvent(Key.Back)` to the same apply path.
   Preview consumes Close/Hide/Dismiss; Exit returns false so the dispatcher leaves once.
3. Introduce `controlsClaimFocus` + `hideControls()` / `showControls()` so focus repair
   cannot re-insist on play/pause during teardown.
4. Document under Unreleased; keep AC-TV-06 / PLAN-TV hide-then-exit wording; correct
   any drift that still describes “exit immediately” as current intent.
5. Branch `bugfix/BUG-032-player-back-hides-controls` from `main` (no `dev` branch).

## Files Touched

- `app-tv/.../player/TvPlayerBackKeys.kt` (new)
- `app-tv/.../player/TvPlayerScreen.kt`
- `app-tv/.../player/TvPlayerBackKeysTest.kt` (new)
- `agile/items|plans|testing/BUG-032-*`
- `docs/AMENDMENTS.md`, `CHANGELOG.md`, `docs/PLAN-TV.md` (as needed)

## Risks & Rollback

- Risk: both paths fire and hide+exit on one press. Mitigation: Exit is not applied in
  the preview path (`tvPlayerBackConsumesKey`).
- Rollback: revert the branch commit; behaviour returns to BackHandler-only hierarchy.
