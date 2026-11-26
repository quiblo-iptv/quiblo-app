# BUG-064: the second film on the television drops a frame in five

Found with the playback info overlay (`FEAT-040`) on 2026-10-20, on a Haier MatrixTV (MediaTek,
Android 14). The stutter `BUG-063` set out to fix, read off the screen instead of guessed at.

## Problem & Motivation

A series plays smoothly the first time. Back out, leave the app, come back and play again, and it
plays at a low frame rate from the first second, with or without a seek. Restarting the app does
not help; clearing its cache does, for a while. TiviMate does the same on the same television.

## What the overlay showed

- **Stuttering**: H.264 1920×960 at 23.976 fps, 2 Mb/s, on `c2.mtk.avc.decoder` (hardware). About
  20 frames a second rendered and 4 dropped, steadily — a fifth of the film, one frame at a time
  (worst run 1, none to a keyframe). The buffer was 26–31 s ahead with no rebuffers: not the
  network. A hardware decoder on a 2 Mb/s stream: not the decoder's speed.
- **Smooth**: another show, H.264 720p, on the same 60 Hz panel: 24 rendered, 0 dropped. So not
  the refresh rate either. Its decoder line was empty — the engine had kept the previous item's
  decoder rather than start one, and the overlay only recorded decoders as they started.

## Root cause

Not proven, and narrowed to one thing. The television's player ViewModel is held by the activity.
Leaving a film *paused* its engine rather than letting it go, so the engine — its hardware
decoder, its buffer still filling — was kept, and the next film played on it. "Leaving the app"
with Back does not end the process on Android 12 and later; it moves the task to the back. Only a
killed process, which is what clearing the cache does on this television, started clean — and the
first film after a clean start was always the smooth one.

## Scope

- Leaving a film or an episode (`PlayerViewModel.onLeft`) writes down the place, then lets go of
  the whole engine (`PlayerController.discardEngine`): released and replaced by a new, idle one.
  Choosing the same title again is a fresh load, from the place written down.
- The overlay shows how many items the current engine has played (`Items on this engine`, amber
  above one), keeps the decoder in use across items, and reads the display's refresh rate every
  second instead of once.

## Explicit Non-Scope

- Live channels: unchanged — stopped on leaving, as `BUG-037` has them.
- The phone: its player ViewModel ends with the screen, so it already started every film clean.
- Sending the app to the background mid-film: still paused in place, so it resumes where it was.

## Acceptance Criteria

- Play an episode, back out, leave the app with Back, return, play again: no dropped frames that
  the first play did not have, and `Items on this engine` reads 1.
- Resume after backing out still starts where the viewer left.
