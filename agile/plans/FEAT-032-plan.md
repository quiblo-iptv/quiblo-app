# FEAT-032 Implementation Plan

## Approach

1. `dimWhilePaused` in `PlayerSettingsStore`, app-wide and defaulting to true — it is about this
   device's screen, not about the viewer, which is the same reasoning as `checkUpdatesOnLaunch`
   and `catalogueSyncInterval`.
2. `PlayerViewModel` exposes it eagerly with `true` as the initial value: a player that holds the
   screen on for one frame after a pause and releases it the next is a panel that flickers.
3. Television: `KeepScreenAwake(enabled = !hasFailed && !(dimWhilePaused && status == PAUSED))`.
4. Phone: split `FLAG_KEEP_SCREEN_ON` out of the immersive `DisposableEffect`, which runs once for
   the life of the screen, into its own effect keyed on whether to keep it awake.
5. A switch on both settings screens.

## Files Touched

- `core/datastore/.../PlayerSettingsStore.kt`, `core/data/.../PlayerSettingsRepository.kt`
- `feature/player/.../PlayerViewModel.kt`, `.../PlayerScreen.kt`
- `app-tv/.../TvPlayerScreen.kt`, `app-tv/.../TvSettingsScreen.kt`
- `feature/settings/.../PlaybackSettingsCard.kt`, `.../SettingsScreen.kt`, `.../SettingsViewModel.kt`

## Risks & Rollback

- Risk: a viewer who pauses to read a subtitle finds the screen off. Mitigated by the setting,
  and by the device's own timeout being minutes rather than seconds.
- Rollback: revert. The flag returns to being held for the life of the screen.
