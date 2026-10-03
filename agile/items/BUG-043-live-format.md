# BUG-043: every live channel is requested as `.ts`

Audit item **PB-4**.

## Problem & Motivation

`XtreamUrl.liveStream()` always appended `.ts`. An account whose provider allows only HLS
(`allowed_output_formats: ["m3u8"]`) failed on every live channel, and nothing told the viewer
why. Where both are allowed, raw MPEG-TS is the less resilient of the two on a mobile network: one
long socket that a dropped packet stalls, where HLS fetches short segments it can retry.

## Environment

- Platform: both
- Source kind: Xtream
- Trigger: an account limited to HLS, or a network on which raw TS stutters
- Check: the audit's manual `curl` — `.ts` fails, `.m3u8` works

## Root cause

`allowed_output_formats` was not parsed, there was no per-source choice, and before `BUG-041` the
URL was built — extension and all — at load time and stored, so nothing could choose at play time.

## Scope

- `UserInfo.allowedOutputFormats` (lower-cased; null when the panel sent no list)
- `SourceResult.Success.allowedLiveFormats`, stored on the source at each refresh that reports it
- A per-source **Live channels** setting — Automatic (default) / HLS / TS — on the edit form of both
  apps (`BUG-042`)
- Automatic = HLS when the account may use it, else TS; TS when the panel never said, which is
  what every account had before
- `sources.liveFormat` and `sources.allowedLiveFormats`: migration 25 → 26
- The extension is chosen when the locator is resolved, at play time (`BUG-041`)

## Explicit Non-Scope

- Showing `active_cons` / `max_connections` on a source details screen. `FEAT-035` diagnoses with
  them; a source screen that shows them belongs with the `FEAT-035` nice-to-have, still open.
- `rtmp`: Media3 does not play it without an extension module, and no panel offers it alone
- A MIME type on the media item — `BUG-044` (PB-5)

## Acceptance Criteria

- An account whose panel allows only `m3u8` plays live channels as `.m3u8` with no setting changed
- An account allowing both plays live as HLS on Automatic, and as TS when the viewer picks TS
- An account whose panel says nothing keeps playing `.ts`, exactly as before
- Films and episodes are untouched
