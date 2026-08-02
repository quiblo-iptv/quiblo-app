# Freeze Prompt — Vibrato

**Tagline:** a vibe-coded IPTV player. Free, open source, GPLv3. Bring your own playlist.
**Repo / org:** `vibrato-tv`
**Application ID:** `dev.vibrato.player`
**Status:** FROZEN for v1.0
**Date:** 2026-08-03
**Purpose:** This is the canonical description of the project. Any new contributor, or any AI agent asked to work on this codebase, is given this document first. Nothing outside this scope is built until v1.0 ships. Scope changes require an explicit amendment at the bottom of this file.

---

## 1. One-sentence definition

**Vibrato** is a free, open-source, GPLv3-licensed Android IPTV **client** that plays Live TV, VOD, and Series from playlists the user supplies themselves.

**Naming note:** the name was chosen deliberately to avoid the "VIPTV" / "VIP TV" namespace, which is saturated with paid subscription resellers. Do not reintroduce that string in the package ID, repo name, store listing, or documentation — the distance from that space is a legal and reputational asset, not an accident.

## 2. What this project is NOT

These are non-goals. Rejecting them is a decision, not an oversight.

- **Not a content service.** The project hosts, indexes, bundles, aggregates, and distributes zero streams. It ships with no default playlist, no channel directory, no "discover content" feature, and no built-in provider list.
- **Not a backend.** There is no server component, no user accounts, no telemetry, no cloud sync, no remote configuration.
- **Not a DRM client.** No Widevine, no ClearKey, no PlayReady in v1.
- **Not a TV app in v1.** Android TV, Google TV, LG webOS, Linux desktop, and Xbox are explicitly phase 2 or later.
- **Not a downloader/recorder.** No recording, no catch-up, no timeshift in v1.

## 3. Locked decisions

| Area | Decision |
|---|---|
| License | **GPLv3** |
| Language | **Kotlin** |
| UI | **Jetpack Compose** (Material 3) |
| Player | **AndroidX Media3 / ExoPlayer** |
| Platform (v1) | **Android phones**, portrait-first, tablet-tolerant |
| minSdk | **30** (Android 11) |
| Sources | **M3U/M3U8** (remote URL + local file) and **Xtream Codes API** |
| EPG | **Xtream API only.** M3U playlists have no guide in v1. |
| Formats | **HLS, raw MPEG-TS, progressive MP4/MKV.** No DRM. |
| Content types | **Live TV, VOD, Series** |
| Storage | **Local only** (Room), with manual export/import to a file |
| Distribution | **GitHub Releases (APK)** |
| Network | Direct client-to-provider. No proxy, no relay, no intermediary. |

### Rationale for the non-obvious ones

- **GPLv3 over GPLv2:** the dependency tree (Media3, Compose, all AndroidX) is Apache-2.0, which is incompatible with GPLv2 but compatible with GPLv3. GPLv3 also carries an explicit patent grant and anti-tivoization terms, satisfying the requirement that the project remain open source through any downstream modification.
- **GPLv3 over AGPLv3:** the AGPL's network clause only triggers when modified code is run as a hosted service. This is a client. The clause would never fire and would falsely signal a server project.
- **Phones before TV:** identical data layer, far faster iteration, and none of the D-pad focus-management cost. TV inherits `:core:*` unchanged.
- **No DRM:** M3U and Xtream providers overwhelmingly serve unencrypted streams. DRM is substantial engineering work serving a near-empty use case here.

## 4. Architectural invariants

These must hold at every commit. A change that breaks one is a design regression regardless of whether tests pass.

1. **No UI code in `:core:*`.** No Compose import, no Android `Context` dependency beyond what Room and DataStore require. TV and desktop frontends must be able to consume `:core` untouched.
2. **The source layer is abstracted.** `MediaSource` is an interface. `M3uSource` and `XtreamSource` are implementations. Adding Stalker, XMLTV, or any future protocol means adding one implementation and zero changes to feature modules.
3. **EPG is source-agnostic.** Even though only Xtream supplies programme data in v1, the EPG storage and query layer accepts programmes from any provider. Adding XMLTV later must not require a schema migration.
4. **Playback is behind an interface.** Feature code never touches `ExoPlayer` directly. It talks to a `PlayerController`. This is the seam where DRM slots in later.
5. **The app never phones home.** The only outbound network traffic is to hosts the user explicitly configured. No analytics, no crash reporting SDK, no update check against a project-controlled server.
6. **Credentials never leave the device.** Xtream username/password are stored encrypted and are never written to logs, exports, or crash traces.

## 5. Glossary

- **M3U** — a plain-text playlist. Each entry carries a display name, optional attributes (`tvg-id`, `tvg-logo`, `group-title`), and a stream URL. Carries no programme schedule data.
- **Xtream Codes API** — a JSON HTTP API exposed by many IPTV panel providers. Authenticated via username/password against a base URL. Returns categorised live channels, VOD, series, and short-range EPG.
- **EPG** — Electronic Programme Guide. What is on now, what is on next.
- **Raw TS** — an unchunked, endless MPEG Transport Stream over HTTP. No adaptive bitrate, no seeking.
- **HLS** — chunked streaming with a `.m3u8` manifest. Supports adaptive bitrate.

## 6. Legal posture

The application is a general-purpose media player, in the same category as VLC or Jellyfin. It has no knowledge of what a user's playlist contains and exercises no editorial control over it. The README must state this plainly and must not link to, recommend, or describe how to obtain any playlist or provider.

## 7. Success condition for v1.0

A user installs the APK, adds either an M3U URL or Xtream credentials, browses categorised Live/VOD/Series content, marks favourites, plays a stream full-screen, and exports their configuration to a file — all offline-tolerant, with no account and no network call to any host they did not enter themselves.

---

## Amendments

_None. Append dated entries below when scope is deliberately changed._
