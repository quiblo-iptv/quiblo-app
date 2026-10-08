# BUG-063: an episode the panel answers with nothing is called an unplayable format

Reported by the owner on 2026-10-08, on 0.27.2, right after a film that `BUG-062` fixed played.

## Problem & Motivation

Episodes failed with one of two lines, both with the account's one screen in use:

```
ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED · no data · account OK (1/1)   → Not sure
no engine error (load timed out) · no data · account OK (1/1)           → "this channel is not broadcasting"
```

The first was never asked again. Other films and channels on the same account played at the same
time, so the `1/1` is not another screen holding the account: it is the panel counting Quiblo's
own request. The fault is in what the panel serves for this series.

## Environment

- Platform: both
- Source kind: Xtream, account with one screen
- Symptom: an episode right after another film or episode

## Root cause

- **An empty answer was classified as a format.** A panel whose screen is still counted answers
  200 with no body. No extractor recognises nothing, so the engine reports an unsupported
  container; `classify` made that `UNSUPPORTED_FORMAT`, which is terminal. Zero bytes had arrived:
  nothing was read, so nothing can have been the wrong format.

## Scope

- `EngineFailure.receivedData`; a container failure with no data is `PROVIDER_REFUSED`, so it is
  retried before the first frame (2 s, 4 s), and if it still fails the screen says the provider
  refused, *may be in use on another screen*.

## Explicit Non-Scope

- How long a panel keeps counting a closed connection: that is the panel's.
- A container failure after data arrived: still `UNSUPPORTED_FORMAT`.
- The verdict. A full account and a stream that sent nothing was tried as `CONNECTION_LIMIT` and
  withdrawn: the panel counts the request it is answering, so `1/1` on a one-screen account is not
  evidence that another screen holds it. The same account played other streams at the same time.

## Acceptance Criteria

- An empty 200 from the panel is retried, and is never called a format.
