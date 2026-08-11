**Playback and Audit Phase of Quiblo**

The 0.2.0 polish pass: one reported playback defect, and an external audit rubric run
against the codebase.

**Created:** 2026-08-05, against commit `140405f`. **Ships as:** `0.2.0` (`versionCode` 2).

---

## Why this document exists

Two things arrived at once. A viewer reported that a series episode stutters — reloading
every couple of seconds, at unchanged quality, where TiviMate plays the same stream
continuously. And a *Master Evaluation & Optimization Framework for Android IPTV
Applications* was brought in as a rubric to grade the app against.

They turned out to be the same story. The rubric's single best technical finding and the
reported bug share a root cause, which is the strongest argument for the rubric that this
phase produced.

## What we did with the rubric

Used it as a **findings generator, not a grading scheme**, and the scoring table was
deliberately ignored. Three reasons, recorded so the decision is not relitigated:

1. **Its weights are flat and the stakes are not.** "RTL mirroring" and "codec fallback
   loop" are both worth ten points. One is polish; the other decides whether the app plays
   anything at all on the cheap boxes the document itself says are most of the market. A
   rubric that can be pushed to "PRODUCTION READY" by adding plurals while the player still
   green-screens is measuring the wrong thing.

2. **It scores the presence of code, not the experience of a user.** Every criterion is
   satisfiable by a call site nobody reaches — which is exactly the failure
   [`001`](001_Bug_Reporting_of_Quiblo.md) already recorded, where nine features
   existed and none could be opened. This document would have scored all nine as passes.
   Only one line in its table is written as an observation rather than an implementation:
   "Wireshark inspection shows 0 background tracking pings." All fifteen should read that
   way.

3. **Twenty of its hundred and fifty points are for things we deliberately do not do.**
   XMLTV parsing and catch-up are `FREEZE.md` non-goals for v1. Scoring against them
   measures scope disagreement, not quality.

Three of its prescriptions were rejected outright on the merits: HTTP/3 is not a lever on
origins the user does not control; the `DefaultTsPayloadReaderFactory` PID tuning is
micro-optimisation Media3 already handles, described in a way that does not match how the
extractor works; and its §5.2 opt-in crash reporting contradicts `FREEZE.md` §4.5, which is
a guarantee and not a checkbox.

One place the rubric is behind us rather than ahead: its "CRITICAL" Pillar 12 wants a regex
scrubber on exported logs. We prevent credentials from entering strings at all — CI greps
for leaked provider URLs, the backup format drops passwords, and `XtreamSourceTest` asserts
no credential reaches an error value. Prevention beats masking. Do not replace it with the
weaker version.

## The stutter

**Reported:** a series episode reloads every couple of seconds; same quality throughout, no
downgrade, no error shown. TiviMate plays the same stream continuously.

**Root cause.** `ENGINE_LOAD_RETRIES = 1` was introduced to keep a *dead live stream* inside
the AC-PLAY-05 fifteen-second budget, and it was applied to every stream regardless of kind.
A series episode is one long read from one host, where a transient hiccup partway through is
normal. With the engine's allowance spent on the first attempt, that hiccup surfaced as a
load error; `scheduleRetry` then restarted the entire media source — new connection, reseek,
rebuffer. Because `STATE_READY` resets the attempt counter, the loop never escalated to an
error and never ended. It stalled for a second or two, every few seconds, indefinitely.

That is a **watchdog tuned for one failure mode misfiring on another**, and it is the second
time this phase that a live-first assumption has cost VOD behaviour. Worth remembering as a
class of bug, not just this instance.

**Why the hiccups were frequent enough to notice.** The player was using Media3's default
`DefaultHttpDataSource`, which is `HttpURLConnection` and holds no pool we control. Every
segment paid a TCP and TLS handshake. The app already used OkHttp for API calls and Coil
already used it for artwork; the player was the one component still on the platform stack.

**Fixed by:**

| Change | Effect |
|---|---|
| Load-error policy split live/VOD | Live keeps fail-fast; VOD gets the engine's ladder back, so a blip retries the same byte range instead of tearing the file down |
| OkHttp-backed `DataSource`, sharing the app's one connection pool | Sockets are reused across segments |
| `setPrioritizeTimeOverSizeThresholds(true)` | The durations the user chose win over byte thresholds a high-bitrate remux hits first |

Local `file://` playlists still resolve: the OkHttp factory is wrapped in
`DefaultDataSource.Factory` rather than replacing it.

**Not yet confirmed on hardware.** The reasoning is sound and the code compiles; the bug was
reported from a device and has to be closed on one. See "What is still open".

## The six enhancements

| # | Item | Outcome |
|---|---|---|
| 1 | Codec fallback | **Done.** `DefaultRenderersFactory.setEnableDecoderFallback(true)` — a hardware decoder that fails now falls through to the next one that claims the format instead of green-screening |
| 2 | OkHttp in the player | **Done.** Shared pool, shared user agent with the API client |
| 3 | Player reuse and zap latency | **Verified, plus instrumentation.** Reuse was already correct; `PlaybackState` now reports time to first frame |
| 4 | `onTrimMemory` | **No change needed.** Coil already does it |
| 5 | RTL and TalkBack | **RTL needed nothing. TalkBack did** |
| 6 | FTS for search | **Rejected on the merits** |

### 4 — why nothing was written

Coil 3.5's `AndroidSystemCallbacks` already registers `ComponentCallbacks2` and does exactly
what the rubric asks: clears the memory cache at `TRIM_MEMORY_BACKGROUND`, halves it at
`TRIM_MEMORY_RUNNING_LOW`, and restricts max size while backgrounded. The rubric's other
half — "release background players" — is already structural: `PlayerController` is a Koin
`factory` released by the player screen, so none is held while browsing (AC-PLAY-09).

Writing our own `onTrimMemory` would have been weaker code fighting Coil's policy, added to
satisfy a checklist. It is recorded here so nobody adds it later believing it is missing.

### 5 — what the audit found

RTL is sound and needed no change: both manifests declare `supportsRtl`, and every alignment
already resolves through `start`/`end`. The remaining `Left`/`Right` names are D-pad keys,
which describe a physical remote and must **not** mirror.

TalkBack was the real gap. A spinner is silent to a screen reader, so a stalled or dead
stream was indistinguishable from a playing one. Buffering now announces politely and
carries the reconnect attempt; a playback error announces assertively. Compose live regions
rather than `announceForAccessibility`, which the rubric prescribes — the View call needs a
View, and this UI has none to hang it on.

### 6 — why not FTS

Two reasons, and the second is the one that settles it.

The browse query is already covered by a purpose-built `(sourceId, kind, sortIndex)` index,
and the `LIKE` runs over an already-narrowed set — one source, one kind — not the whole
table. There is no evidence it is slow.

More importantly, **FTS `MATCH` cannot do infix matching.** `LIKE '%port%'` finds "Sports";
`MATCH 'port*'` does not. Channel search is infix by nature, because people type fragments of
names like "BeIN Sports HD 1". Adopting FTS would break the feature to speed up something we
have not shown is slow.

## Found along the way, not fixed

**The TV player has no error UI.** `TvPlayerScreen` renders a spinner for
`PlaybackStatus.BUFFERING` and nothing at all for `PlaybackStatus.ERROR`. On a television a
dead stream shows a black screen with no message and no retry, while the phone build has
`PlaybackErrorMessage` with both. This is the same shape as the bugs in `001` — parity
assumed, not verified — and it belongs on its own sheet rather than smuggled into this phase.

Written up as **#011** in [`004`](004_Bug_Reporting_of_Quiblo_—_Round_2.md).
It is a release blocker: the television frontend does not meet AC-PLAY-05.

## What is still open

| Item | Needs |
|---|---|
| The stutter is fixed in reasoning only | A device run on the same episode, comparing rebuffer counts before and after |
| Zap latency against the sub-500ms target | The new `loadTimeMillis`, read on hardware |
| Search timing against AC-FAV-05 | A real playlist at twenty thousand rows |
| ~~TV error UI~~ | **Closed.** Reported as #011, fixed, and confirmed on the Haier |
| Codec fallback | A box that actually fails to decode; it cannot be proven on a device that never had the problem |

The first three no longer need a build putting on anything: 0.2.0 is installed on the Haier
as of 2026-08-05, and `rebufferCount` and `loadTimeMillis` are in it. What they need now is
somebody watching an episode and reading the numbers off.

An FFmpeg software decoder — the rubric's full Pillar 6 — is a **separate decision**, not an
omission. Media3's FFmpeg extension is not on Maven and has to be built from source, which
is a real change to how this project is built and released. `setEnableDecoderFallback` buys
the common case without it.

## Version

`0.1.0-alpha01` → **`0.2.0`**, `versionCode` 2.

The old string disagreed with the README, which called the app a release candidate, and with
the release checklist, which called it v1.0.0. Three names for one build. `0.2.0` is now the
only one, and the README no longer claims a candidacy that was never true.
