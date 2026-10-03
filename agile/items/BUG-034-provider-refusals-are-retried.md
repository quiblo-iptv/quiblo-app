# BUG-034: every refused stream is called "no longer available" and never asked again

Audit item **PB-1**, with **T-1** delivered inside it.

## Problem & Motivation

A household reported that streams "sometimes" do not play, and could not tell whether the provider
or Quiblo was at fault. Pressing Retry a few seconds later usually worked.

Every HTTP failure the engine reported — 401, 403, 429, 458, 503, 509 alike — was mapped to
`PlaybackError.SOURCE_GONE`, shown as *This stream is no longer available at that address.*, and
treated as terminal. On top of that, nothing at all was retried before the first frame. Xtream
panels routinely answer 403, 458 or 509 while an account's connection slot has not yet been
released — right after zapping from another channel, or while another screen still holds it — and
a second try two seconds later is normally all it takes.

`:core:media` also had no tests at all (`T-1`). The two decisions most likely to cause this
symptom — what kind of failure it is, and whether to try again — lived inside an ExoPlayer
listener where nothing could reach them.

## Environment

- Platform: both (`:app`, `:app-tv`)
- Source kind: any; most visible on Xtream accounts with one or two connections
- Trigger: the panel refusing a stream with a status other than 404
- Symptom: "no longer available", immediately, on a channel that works a moment later

## Root cause

- `toPlaybackError()` mapped `ERROR_CODE_IO_BAD_HTTP_STATUS` to `SOURCE_GONE` without reading the
  status, which sits on `HttpDataSource.InvalidResponseCodeException` in the cause chain.
- `SOURCE_GONE` was in `scheduleRetry`'s terminal set, so even a mid-playback refusal stopped.
- `scheduleRetry` returned an error for anything when `!hasEverBeenReady`.
- `PlaybackError.UNREACHABLE` had a string and was never produced; unknown hosts and refused
  connections became `TIMEOUT`.
- `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` fell through to `UNKNOWN`.

## Scope

- Read the status: 404/410 → `SOURCE_GONE`; 401 → new `AUTH_REJECTED`; 403, 429, 458, 46x, 509
  and every 5xx → new `PROVIDER_REFUSED`; anything else → `UNKNOWN`, never a guess
- `PROVIDER_REFUSED` and `TIMEOUT` retried up to twice before the first frame (2 s, then 4 s),
  only when the wait still ends inside the 12 s watchdog
- Unknown host / refused connection / no route → `UNREACHABLE`
- `PARSING_CONTAINER_UNSUPPORTED` → `UNSUPPORTED_FORMAT`
- The watchdog reports the last real failure rather than a generic timeout if it fires mid-retry
- Both decisions as pure functions (`classify`, `nextStep`) with the first tests in `:core:media`

## Explicit Non-Scope

- Saying *whose* side failed — the account check and the verdict are `FEAT-035` (D-1). The two new
  enum values are engine-level inputs to it; their interim wording is replaced there.
- Behind-live-window recovery — `BUG-036` (PB-2)
- Releasing the connection while paused or backgrounded — `BUG-037` (PB-3)
- The HLS fallback for an extension-less playlist — `BUG-044` (PB-5)

## Acceptance Criteria

- A 403/458/509 on the initial load is retried twice before an error is shown, and the error
  still appears within `AC-PLAY-05`'s 15 s
- A 404 or 410 is reported at once, as before
- A 401 is reported at once with wording about the account, not about the stream
- A stream that played and then drops still gets `AC-PLAY-06`'s three backed-off attempts, and a
  refusal mid-playback is now among them
- `:core:media:testDebugUnitTest` exists and runs in CI's `./gradlew test`
