<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Quiblo

**A free, open source IPTV player for Android. Bring your own playlist.**

[![CI](https://github.com/quiblo-tv/quiblo/actions/workflows/ci.yml/badge.svg)](https://github.com/quiblo-tv/quiblo/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Quiblo plays Live TV, movies, and series from playlists **you** supply — an M3U/M3U8
URL or file, or an Xtream Codes account. No ads, no accounts, no tracking, no backend.

> **Status: release candidate.** Milestones M0–M6 are complete: sources, playback,
> browse, favourites, Xtream, EPG, export/import, and release engineering. What remains
> before v1.0.0 is the full acceptance sweep on physical devices
> ([`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md)). See [`docs/PLAN.md`](docs/PLAN.md) for the
> roadmap.

## Quiblo supplies no content

**This app ships with no playlists, no channel list, no provider directory, and no way to
find any.** It is a player, in the same category as VLC or mpv. It has no knowledge of
what your playlist contains and exercises no control over it.

**You are solely responsible for the sources you configure and for the legality of
accessing them in your jurisdiction.** If you do not already have a playlist or an IPTV
subscription, this app is of no use to you, and the project will not help you find one.

Requests for sources, providers, or bundled content are closed without discussion. Posting
a playlist URL, provider hostname, or credential anywhere in this project results in an
immediate ban — see [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Features

| Feature Area | Details |
|---|---|
| **Sources** | M3U/M3U8 by URL or local file, and Xtream Codes API |
| **Content & Series** | Live TV, movies, series with seasons & episode breakdown |
| **Guide & EPG** | Now/next per channel, fetched on demand and cached |
| **Player Controls** | Play/pause, seek, configurable skip interval, Start Over, subtitle & audio track selection, screen lock |
| **Picture & Sound** | Fit/Fill/Zoom/Stretch aspect modes, drag for brightness and volume, full-screen playback that keeps the screen awake |
| **Playback Tuning** | Skip interval, buffering profile and a maximum-bitrate cap, all in Settings |
| **Movies & Series** | Detail screens with artwork and overview, and Resume or Start from beginning |
| **Continue watching** | A row of what you started and have not finished, with how far in you were. A series appears once, at the episode you were last on |
| **Layout & Search** | Poster grids for movies and series, category filter, list/grid toggle, expandable search |
| **Theme & Design** | Material 3 Expressive, dark and light |
| **Resume** | Playback position remembered per item, and per episode within a series |
| **Favourites** | Survive playlist refresh |
| **Film information** | Optional descriptions, genres, certificates, ratings, director and cast from The Movie Database, using your own API key. Off by default |
| **Backup** | Versioned export and import of sources and favourites, to a file you choose |
| **Storage & Privacy** | Entirely on-device with zero telemetry |

Not yet implemented, in rough priority order: an EPG time-grid, a quick-zap overlay, a
continue-watching carousel, catchup/timeshift, and picture-in-picture. Several are Phase 2
in [`docs/PLAN.md`](docs/PLAN.md) §6.

## Privacy

Quiblo has no telemetry, no analytics, no crash-reporting SDK, and no update check
against any project-controlled server. The only outbound connections are to the hosts you
configure yourself — your playlist or Xtream provider, and The Movie Database only if you
have entered your own API key for it. Quiblo ships no key of its own, so with the setting
untouched nothing ever contacts that service. This is verifiable by packet capture on a clean install, and is a
release criterion (AC-NFR-03).

Permissions requested: `INTERNET` and `ACCESS_NETWORK_STATE`. Nothing else — file access
goes through the system document picker, so no storage permission is needed.

Xtream credentials are stored encrypted on-device and are never written to logs, export
files, or crash traces.

## Building

Requires **JDK 17+** and the **Android SDK** (platform 37, build-tools 37.0.0). Android
Studio is not required.

```bash
git clone https://github.com/quiblo-tv/quiblo.git
cd quiblo
./gradlew build             # compile, test, detekt, lint
./gradlew :app:installDebug     # phone and tablet
./gradlew :app-tv:installDebug  # Android TV / Google TV
```

Minimum supported device: **Android 11 (API 30)**.

**There are two apps, and a release ships both APKs.** `:app` is the phone and tablet build
(`dev.quiblo.player`); `:app-tv` is the television build (`dev.quiblo.tv`), with a D-pad
interface and a leanback launcher entry. They are separate application ids, so both can be
installed at once, and they share every layer below the UI. The phone APK will install on a
television and then never appear in its launcher — take the TV one there.

## Architecture

Multi-module Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer, Room, Ktor, Koin.

```
:app                 phone assembly, navigation graph, DI wiring, theme
:app-tv              television assembly and its D-pad UI
:core:*              model, common, database, datastore, network, media, data
:source:*            api, m3u, xtream, tmdb — the MediaSource abstraction and its implementations
:feature:*           browse, sources, live, vod, series, player, favorites, settings
```

`:core:*` and `:source:*` contain no UI code and never import Compose — enforced by the
build, not by convention. That is what let the television frontend be a presentation layer
only: `:app-tv` reuses the same ViewModels rather than forking them, so a behaviour change
lands in one place and reaches both apps. See [`docs/PLAN.md`](docs/PLAN.md) §2 and
[`docs/PLAN-TV.md`](docs/PLAN-TV.md) §2.

## Documentation

| Document | What it is |
|---|---|
| [`docs/FREEZE.md`](docs/FREEZE.md) | The canonical, frozen scope for v1.0. Read this first. |
| [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md) | Definition of done, as numbered binary criteria |
| [`docs/PLAN.md`](docs/PLAN.md) | Stack, module structure, milestones |
| [`docs/PLAN-TV.md`](docs/PLAN-TV.md) | The Android TV / Google TV frontend: target hardware, design, milestones |
| [`docs/ACCEPTANCE-SWEEP.md`](docs/ACCEPTANCE-SWEEP.md) | What has actually been verified on hardware, and what is left |
| [`docs/RELEASING.md`](docs/RELEASING.md) | Signing key handling and the release process |
| [`agile/`](agile) | Reported bugs and the plan being worked against them |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to contribute, and what will get you banned |
| [`SECURITY.md`](SECURITY.md) | Private vulnerability disclosure |

## Licence

GPLv3-or-later. See [`LICENSE`](LICENSE).

Quiblo is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version. It is distributed in the hope that
it will be useful, but **without any warranty**; without even the implied warranty of
merchantability or fitness for a particular purpose.
