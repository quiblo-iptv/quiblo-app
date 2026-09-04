# FEAT-032: a paused player lets the screen dim

## Problem & Motivation

Both players hold `FLAG_KEEP_SCREEN_ON` for the whole life of the player screen. The reason for
holding it is sound and stated in the code: watching a film involves no input, so the display
timeout treats a viewer as an idle user and dims the picture mid-scene.

That reason stops being true the moment playback stops. A pause at bedtime is a television at
full brightness on a still frame until morning — wasted power on any panel, and on an OLED a
still frame held for hours is not only wasted power.

## Environment

- Both apps: `feature/player/PlayerScreen` and `app-tv/.../TvPlayerScreen`
- Trigger: pausing and walking away

## Scope

- While paused, stop declaring the window as being watched
- An app-wide setting to turn that off, on by default

## Explicit Non-Scope

- Changing the screen's brightness directly. The system's own timeout is what should decide, and
  an app that dims a panel itself fights the setting the viewer already has.
- Anything about a failed stream, which already releases the flag.

## Acceptance Criteria

- Playing holds the screen on, as before
- Paused releases it, so the device's own display timeout applies
- Buffering and the next-episode offer still count as watching
- Turning the setting off keeps the old behaviour exactly
- On by default
