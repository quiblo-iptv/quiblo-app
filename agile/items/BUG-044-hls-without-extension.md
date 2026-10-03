# BUG-044: an HLS playlist without `.m3u8` at the end of its path is played as a file

Audit item **PB-5**.

## Problem & Motivation

M3U entries such as `…/play.php?id=12`, `…/stream?output=m3u8`, extension-less paths, and URLs
that redirect to a playlist failed with *This stream is in a format Quiblo cannot play* — while the
same URL played in VLC or mpv. Nothing was wrong with the stream; Quiblo had asked the wrong reader
to open it.

## Environment

- Platform: both
- Source kind: mostly M3U (Xtream live is `.ts`, or `.m3u8` since `BUG-043`)
- Symptom: `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`, at once, on a stream other players accept

## Root cause

`toMediaItem()` set only the URI. `DefaultMediaSourceFactory` infers the content type from the
path's extension and nothing else, so a playlist named in the query string — or served from a path
with no extension — went to the progressive extractors, none of which recognises a playlist.

## Scope

- `PlayableItem.mimeType` (optional)
- `hlsMimeTypeFor(url)`: HLS when the path ends `.m3u8` **or** the query names `m3u8` as a value or a
  file; set on the media item when the item does not say otherwise
- One retry as HLS when nothing declared the type, the path names no container the engine knows,
  and the failure is `PARSING_CONTAINER_UNSUPPORTED` — inside the existing 12 s watchdog budget
- Both decisions as pure functions with tests

## Explicit Non-Scope

- A probe request (HEAD or a range read) to learn the type first — it costs a connection, and on an
  account allowed one that is the connection the stream needed
- DASH and SmoothStreaming without extensions: not seen in IPTV playlists
- M3U per-channel headers — `BUG-045` (PB-6)

## Acceptance Criteria

- `…/play.php?file=index.m3u8` and `…?output=m3u8` play as HLS on the first attempt
- An extension-less URL that serves a playlist plays after one fallback, inside 15 s
- A `.ts`, `.mkv` or `.mp4` that fails is not retried as HLS
- A stream that is neither still shows an error within `AC-PLAY-05`'s budget
