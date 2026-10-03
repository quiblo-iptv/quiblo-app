# BUG-045: a dead provider subtitle link stops the whole film

Audit item **VOD-1** (updated audit, 2026-10-03).

## Problem & Motivation

A film played, the viewer turned on the panel's subtitle — or the device had captions switched on
in its accessibility settings, which the engine follows at start — and the film stopped with
*This stream is no longer available at that address*. Retry selected the same subtitle and failed
again. The film's own URL was fine; the subtitle URL the panel listed was dead.

Panels list dead subtitle links often, and only some films have them, which is exactly what
"films sometimes don't play" looks like from the sofa.

## Environment

- Platform: both
- Source kind: Xtream (`get_vod_info` → `info.subtitles`)
- Symptom: a playback error the moment a subtitle is selected, or at start with captions on

## Root cause

Every film got the panel's subtitle list attached to its `MediaItem` as sidecar subtitles,
unchecked. Media3 1.10.1 wraps each in a lazily loaded `ProgressiveMediaSource` merged into a
`MergingMediaSource`; the file is fetched only when its track is selected, and a load failure
surfaces through `SampleStream.maybeThrowError` as a fatal error **for the whole item**.

## Scope

- The engine is never handed a panel's subtitle URL. The panel's subtitles are offered in the
  menu; choosing one fetches it (5 s, the picked-file size cap, format sniffed from the bytes, an
  HTML error page refused) into app storage and prepares the film again at the same moment with
  the local copy showing.
- One that cannot be fetched stays in the menu marked *(unavailable)*, a notice says so, and the
  film is not touched.
- Safety net in the controller: a playback error while a text track is selected switches text off
  and prepares again at the same position, once per item, with the same notice.

## Explicit Non-Scope

- Fetching every listed subtitle up front — it would hold up the start of every film on the
  panel, which is the delay `VOD-2` removes, not one to add.
- Remembering fetched copies against the title: the panel offers them again next time.
- Not awaiting the details call before `prepare()` — `BUG-046` (VOD-2).

## Acceptance Criteria

- A film whose panel lists a dead subtitle plays; choosing that subtitle says it could not be
  loaded and the film carries on.
- A film whose panel lists a working subtitle shows it after one short restart at the same moment.
- No `PlayableItem` handed to the controller carries an `http(s)` subtitle URL.
