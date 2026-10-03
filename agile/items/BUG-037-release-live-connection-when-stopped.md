# BUG-037: a paused or backgrounded live channel keeps the account's connection

Audit item **PB-3**.

## Problem & Motivation

On an account allowed one connection, a live channel left paused in the background — the app sent
to the home screen, the television switched to another input, or on the television simply backing
out of the player to the catalogue — went on holding that connection. The next device was refused,
and so was Quiblo itself after a channel change (which `BUG-034` then had to retry around). On
return, play resumed a stale buffer of a broadcast that had moved on, and then failed.

## Environment

- Platform: both; worst on the television, whose player ViewModel outlives the player screen
- Source kind: any live stream; visible on accounts with `max_connections` of 1 or 2
- Trigger: `ON_STOP`, or leaving the television's player screen
- Symptom: "in use on another screen" from the provider, on the only screen in use

## Root cause

`PlayerViewModel.onStopped()` only called `controller.pause()`. A paused ExoPlayer keeps loading
to fill its buffer, so the HTTP connection — for raw `.ts` live, one long socket — stays open.
A channel change replaced the media item without closing the previous load first, so for a moment
both connections were open and a one-connection panel refused the new one.

## Scope

- `PlayerController.stop()` (Media3 `player.stop()`: releases loaders and sockets, keeps the item)
- `onStopped()`: live → `stop()`; VOD → pause and save the position, as before
- `onStarted()`: a live channel that was let go of is started again at the live edge
- `onLeft()`: the television leaving its player stops a live channel and forgets the request, so
  choosing the same channel again loads it afresh
- `prepare()` stops the previous item first when it was live, before the new item is set

## Explicit Non-Scope

- Background playback or picture-in-picture — none in v1 (`docs/FREEZE.md` §2)
- How quickly a panel itself notices a closed connection; that is the panel's

## Acceptance Criteria

- Backgrounding a live channel closes its connection; returning plays it again, at live
- Backgrounding a film pauses it and keeps its position
- Leaving the television's player on a live channel closes its connection, and choosing the same
  channel again plays it
- Zapping between live channels never holds two connections at once
