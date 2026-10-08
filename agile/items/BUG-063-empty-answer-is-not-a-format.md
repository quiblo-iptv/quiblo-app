# BUG-063: an episode the panel answers with nothing is called an unplayable format

Reported by the owner on 2026-10-08, on 0.27.2, right after a film that `BUG-062` fixed played.

## Problem & Motivation

Episodes failed with one of two lines, both with the account's one screen in use:

```
ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED · no data · account OK (1/1)   → Not sure
no engine error (load timed out) · no data · account OK (1/1)           → "this channel is not broadcasting"
```

The first was never asked again; the second blamed a channel, on an episode.

## Environment

- Platform: both
- Source kind: Xtream, account with one screen
- Symptom: an episode right after another film or episode

## Root cause

- **An empty answer was classified as a format.** A panel whose screen is still counted answers
  200 with no body. No extractor recognises nothing, so the engine reports an unsupported
  container; `classify` made that `UNSUPPORTED_FORMAT`, which is terminal. Zero bytes had arrived:
  nothing was read, so nothing can have been the wrong format.
- **The verdict ignored the full account when nothing arrived.** A healthy account with a timeout
  and no data was always `CHANNEL_OFFLINE`, even when the panel said every screen was in use.

## Scope

- `EngineFailure.receivedData`; a container failure with no data is `PROVIDER_REFUSED`, so it is
  retried before the first frame (2 s, 4 s), and if it still fails the screen says the provider
  refused, *may be in use on another screen*.
- Verdict: every screen in use and a stream that sent neither a status nor a byte is
  `CONNECTION_LIMIT`.

## Explicit Non-Scope

- How long a panel keeps counting a closed connection: that is the panel's.
- A container failure after data arrived: still `UNSUPPORTED_FORMAT`.

## Acceptance Criteria

- An empty 200 from the panel is retried, and is never called a format.
- With the account at its limit, a stream that sent nothing says *Your account is already in use
  on 1 of 1 allowed screens*.
