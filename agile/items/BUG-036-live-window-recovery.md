# BUG-036: a live HLS channel that falls behind its window never recovers

Audit item **PB-2**.

## Problem & Motivation

After a stall, a pause, or a return from the background, a live HLS channel would buffer, then
say "Reconnecting… (attempt 1)", 2, 3, and then show an error — on a channel that was broadcasting
perfectly well. Pressing Retry did the same thing again.

## Environment

- Platform: both
- Source kind: any live HLS stream (`.m3u8`), Xtream or M3U
- Trigger: playback position falling out of the playlist's live window
- Symptom: three reconnect attempts, then "This stream could not be played."

## Root cause

`ERROR_CODE_BEHIND_LIVE_WINDOW` was not handled. It classified as `UNKNOWN`, and both the automatic
retry and the manual Retry called `player.prepare()` at the old position — which was still outside
the window, so every attempt failed the same way.

## Scope

- `BEHIND_LIVE_WINDOW` → `seekToDefaultPosition()` + `prepare()` at once, not counted as a retry,
  bounded at three rejoins between plays
- For a live item, both the automatic retry and the manual Retry restart at the live edge
- A retry clears the previous failure's evidence from the state (left over from `FEAT-035`)

## Explicit Non-Scope

- Releasing the connection while paused or in the background — `BUG-037` (PB-3), which also
  re-prepares at the live edge when the screen comes back
- VOD: a film has a position worth keeping, and is restarted where it was

## Acceptance Criteria

- A live HLS channel that falls behind its window resumes at the live edge with no error and no
  "Reconnecting" count
- Retry on a failed live channel starts at the live edge
- A server whose window is genuinely broken still ends in an error rather than a loop
