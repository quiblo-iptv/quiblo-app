# BUG-062: a film that is slow to start fails at twelve seconds

Reported by the owner on 2026-10-08, on the television: most films and episodes on one Xtream
account stopped with *Not sure — could not tell whether the problem is your account, the provider
or Quiblo*. Live channels were fine.

## Problem & Motivation

The details line under the verdict read:

```
no engine error (load timed out) · data received · account OK (1/1) · host <panel>
```

The account was fine and the panel had sent data, yet the film never started and nothing said why.
On that account it was most of the catalogue, which is "films don't play" from the sofa.

## Environment

- Platform: both
- Source kind: Xtream (any source with progressive films and episodes)
- Account: one screen
- Symptom: the error screen 12 s after choosing a film, with the line above

## Root cause

Three things, one after the other:

1. **The watchdog's budget is shorter than the engine's own read timeout.** The initial load is
   failed at 12 s (`INITIAL_LOAD_TIMEOUT_MILLIS`, AC-PLAY-05); the shared OkHttp client waits 15 s
   on a read. A film's start is several requests — the header, then the index a Matroska or MP4 file
   keeps at its far end — and a panel that is slow to answer one of them leaves the read waiting.
   The watchdog fires first, so the engine never reports an error, and the error is never retried:
   `no engine error (load timed out)`.
2. **The watchdog did not stop the engine.** The load it had given up on carried on underneath the
   error, holding the connection. On a one-screen account that connection *is* the screen — the
   `1/1` in the line is Quiblo's own hanging request.
3. **So *Try again* did nothing.** `retry()` calls `prepare()`, which the engine ignores unless it
   is idle. The hanging load was left to hang, and the watchdog failed it again.

## Scope

- A film or an episode that has received data is not failed at 12 s (`afterLoadTimeout`):
  - still receiving — waited for, in 2 s steps, up to 40 s from the start;
  - received data and then nothing for 4 s — stopped and loaded again once, where it was, with a
    full 12 s of its own;
  - stalled again, or 40 s reached — the error, as before.
- Whenever the watchdog reports the timeout, the engine is stopped first: the connection is closed
  and *Try again* starts a real load.

## Explicit Non-Scope

- Live channels: unchanged. A channel that has not started in 12 s fails at 12 s.
- A film that received nothing: unchanged, the error at 12 s. That is the dead or unreachable
  stream AC-PLAY-05 is about.
- The read timeout of the shared client: it serves the API too, and the watchdog no longer depends
  on it.
- The diagnosis: a film that still times out is still *Not sure*, which is the honest verdict.

## Acceptance Criteria

- A film whose panel takes 15–35 s to deliver the start of it plays, and is not reported.
- A film whose panel sends the header and then stalls is loaded again once; the line says
  `1 retry` if it still fails.
- After any timeout, *Try again* starts the film again, and the panel no longer counts a screen for
  the abandoned load.
- A live channel, and a film that sent nothing, still fail within 15 s.
