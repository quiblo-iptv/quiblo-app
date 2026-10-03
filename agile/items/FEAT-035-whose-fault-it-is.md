# FEAT-035: a failed stream says whose side the problem is on

Audit item **D-1**.

## Problem & Motivation

The owner's requirement, in his words: when a stream fails, the message must tell me whether *my
subscription ended*, *the provider is down*, or *the app is broken*.

It said none of those. `toPlaybackError()` reduced the engine's report to a seven-value enum and
threw the rest away, and every message was about "this stream". A viewer whose subscription
expired yesterday was told *This stream is no longer available at that address* — the words used
for a dead channel and for a Quiblo bug alike — and the HTTP status that would have said which side
refused was nowhere to be seen. The next failure has to answer the question by itself.

`UserInfo.isExpired` also read only `status == "Expired"`. Many panels leave the status at
`Active` after `exp_date` has passed, so an expired account was not recognised even at refresh.

## Environment

- Platform: both (`:app`, `:app-tv`)
- Source kind: both; the account check is Xtream only, M3U is judged on the stream's own answer
- Trigger: any playback failure, after `BUG-034`'s retries are spent

## Root cause

There was no evidence to give a verdict from. The status, the engine's error name, whether any
bytes arrived and whether the item had ever played were all discarded at the engine boundary, and
nothing asked the provider about the account once playback had failed.

## Scope

- `AccountHealth` and `MediaSource.accountHealth()` / `checksAccount` in `:source:api`
- Xtream: one `player_api.php` call, 5 s timeout, through the panel rate limiter and block gate;
  `active_cons` parsed; `exp_date < now` is expired whatever `status` says — at diagnosis *and*
  at refresh
- `FailureDetails` on `PlaybackState`: HTTP status, engine error name, bytes received, had played,
  retries
- `PlaybackDiagnoser` in `:core:data`: connectivity, account (cached 60 s per source), stream →
  the pure `verdict()`; a redacted details line naming the host only
- Eleven verdicts in five sides, worded in `feature/player` strings, shown on both apps' error
  screens; "Checking why…" while the check runs
- **Copy details** on the phone; a 20-entry in-memory **Playback log** under Settings → App on both

## Explicit Non-Scope

- The "nice to have" — expiry and screens on the source details screen, and a Home banner three
  days before expiry. They read the same `AccountHealth` and belong with `BUG-042` (SEC-2), which
  adds the source details screen they would sit on.
- A "Report" action that sends anything anywhere. The app never phones home (`docs/FREEZE.md` §4.5).
- Persisting the log. Memory only, by requirement.

## Acceptance Criteria

- Expired test account → "Your subscription ended on …" on phone and TV
- Wrong password → the credentials verdict, not "no longer available"
- Panel host down, device online → the provider verdict
- Airplane mode → the connection verdict
- A deliberately unsupported stream on an active account → the Quiblo verdict
- Account check unavailable → "Not sure", never a guess
- The error first appears within `AC-PLAY-05`'s budget; the verdict replaces it in place
- No username, password or stream path in anything shown, copied or kept (`AC-XT-04`)
