# BUG-032: TV player Back does nothing while controls are up

## Problem & Motivation

On a real television remote, when the player controls are on screen, the first Back
press is supposed to hide them (AC-TV-06). Viewers reported that Back did nothing —
a focus race — so they could not leave the playable without waiting for timeout or
other workarounds.

This is **CODE ORANGE** (Amendment 9): an earlier fix (`bugfix/player-back-exit`,
PR #59) made Back exit immediately (wrong product). TV UI v23 restored hide-then-exit
in `BackHandler` only and dropped the key preview path some remotes need, so the
stuck-with-controls symptom returned.

## Environment

- Platform: Android TV (`:app-tv`)
- Symptom: controls visible → Back appears dead
- Intended: first Back hides controls; second Back exits playback

## Scope

- Dual delivery of Back (key preview + `BackHandler`) from one pure decision function
- Stand focus-repair down before hiding controls
- Regression tests and docs synced to hide-then-exit

## Explicit Non-Scope

- Changing phone `:feature:player` back behaviour
- Changing shell back-to-exit on Search (`FEAT-029`)
- Reintroducing “exit immediately” as the product rule

## Acceptance Criteria

- With controls up and a transport button focused, Back hides controls without pausing
- With controls down, Back leaves playback
- Track menu still closes on Back before controls are touched
- `TvPlayerBackKeysTest` fails if controls intercept becomes “exit immediately” again
