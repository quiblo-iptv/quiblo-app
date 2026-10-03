# BUG-041: the Xtream password is stored in plain text inside every channel URL

Audit item **SEC-1**.

## Problem & Motivation

`EncryptedCredentialStore` exists so that an Xtream account's password is never in the database
in plain text (`AC-XT-04`). It was there anyway — in the `channels` table, once per channel, film
and episode, because the stream URL the panel serves has the username and password in its path:
`http://host/live/USER/PASS/101.ts`. `XtreamUrl`'s own KDoc said the opposite.

And an episode's stream URL is also its identity in history, so the same password was written into
`resume_positions`, `watch_events`, `picked_subtitles` — and from there into every backup file.

## Environment

- Platform: both
- Source kind: Xtream
- Where: `channels.streamUrl`; every history table keyed by an episode; navigation arguments
  carrying an episode's URL; backups

## Root cause

`XtreamSource` built the full credentialed URL at load time (`XtreamUrl.liveStream` etc.) and
stored it as `Channel.streamUrl` and `Episode.streamUrl`. Nothing ever resolved a URL at play
time, so the URL had to be stored whole.

## Scope

- Store a locator with neither host nor credentials: `xtream:live/101`, `xtream:movie/7.mkv`,
  `xtream:series/501.mp4`
- `MediaSource.playbackUrl(request, locator)` — default unchanged; Xtream builds the URL from the
  credential store at the moment of playing. `ChannelRepository.playbackUrl` and `PlayerViewModel`
  use it; the URL lives only in the player's memory
- Migration 24 → 25 (data only — schema unchanged): stored Xtream URLs in `channels` and in every
  history table become locators, so existing resume points, watch history and picked subtitles keep
  working
- Live locators carry no extension, so `BUG-043` (PB-4) can choose the container at play time

## Explicit Non-Scope

- Editing a source's URL or password — `BUG-042` (SEC-2), which this makes cheap: no stored row
  holds the old host or password any more
- M3U playlists whose own URLs carry credentials — `FEAT-050` (OT-1) for the backup; the playlist's
  entries are the playlist's
- Encrypting the database

## Acceptance Criteria

- After a refresh, no `channels` row of an Xtream source contains the username, password or host
- After upgrading, the same holds for rows stored by older versions, and for every history table
- An episode half-watched before the upgrade still offers Resume at the same position afterwards
- Playback is unchanged, and a password changed in the credential store is used by the next play
