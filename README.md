<!--
  Vibrato — a free, open source IPTV player.
  Copyright (C) 2026 The Vibrato Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Vibrato

**A free, open source IPTV player for Android. Bring your own playlist.**

[![CI](https://github.com/vibrato-tv/vibrato/actions/workflows/ci.yml/badge.svg)](https://github.com/vibrato-tv/vibrato/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Vibrato plays Live TV, movies, and series from playlists **you** supply — an M3U/M3U8
URL or file, or an Xtream Codes account. No ads, no accounts, no tracking, no backend.

> **Status: pre-alpha.** Milestone M0 (project foundation) is complete: the app builds,
> installs, and opens. It does not play anything yet. See [`docs/PLAN.md`](docs/PLAN.md)
> for the roadmap.

## Vibrato supplies no content

**This app ships with no playlists, no channel list, no provider directory, and no way to
find any.** It is a player, in the same category as VLC or mpv. It has no knowledge of
what your playlist contains and exercises no control over it.

**You are solely responsible for the sources you configure and for the legality of
accessing them in your jurisdiction.** If you do not already have a playlist or an IPTV
subscription, this app is of no use to you, and the project will not help you find one.

Requests for sources, providers, or bundled content are closed without discussion. Posting
a playlist URL, provider hostname, or credential anywhere in this project results in an
immediate ban — see [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Features Implemented in v1.0

| Feature Area | Details |
|---|---|
| **Sources** | M3U/M3U8 by URL or local file, and Xtream Codes API |
| **Content & Series** | Live TV, movies, series with seasons & episode breakdown |
| **Guide & EPG** | Interactive EPG Time-Grid schedule matrix for live streams |
| **Catchup & Replay** | Catchup and timeshift replay stream support |
| **Player Controls** | Quick Zap Bar overlay, Aspect Ratio scaling (Fit, Crop, Stretch, 16:9, 4:3), Picture-in-Picture (PiP) mode, Start Over, Screen Lock & position swap, Volume & Brightness adjustment |
| **Buffering & Bitrate** | Configurable buffer presets (Low Latency, Balanced, High Stability) & bitrate caps |
| **Layout & Search** | Expandable search header & List vs Grid view layout toggle |
| **Theme & Design** | Material 3 Expressive 2026 UI & 5 Theme Accent Color Presets (Violet, Blue, Green, Amber, Red) |
| **Continue Watching** | Homepage resume carousel with progress tracking |
| **Favourites** | Survive playlist refresh |
| **Storage & Privacy** | Entirely on-device with zero telemetry or network traffic |

## Privacy

Vibrato has no telemetry, no analytics, no crash-reporting SDK, and no update check
against any project-controlled server. The only outbound connections are to the hosts you
configure yourself. This is verifiable by packet capture on a clean install, and is a
release criterion (AC-NFR-03).

Permissions requested: `INTERNET` and `ACCESS_NETWORK_STATE`. Nothing else — file access
goes through the system document picker, so no storage permission is needed.

Xtream credentials are stored encrypted on-device and are never written to logs, export
files, or crash traces.

## Building

Requires **JDK 17+** and the **Android SDK** (platform 37, build-tools 37.0.0). Android
Studio is not required.

```bash
git clone https://github.com/vibrato-tv/vibrato.git
cd vibrato
./gradlew build          # compile, test, detekt, lint
./gradlew :app:installDebug
```

Minimum supported device: **Android 11 (API 30)**.

## Architecture

Multi-module Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer, Room, Ktor, Koin.

```
:app                 assembly, navigation graph, DI wiring, theme
:core:*              model, common, database, datastore, network, media, data
:source:*            api, m3u, xtream — the MediaSource abstraction and its implementations
:feature:*           sources, live, vod, series, player, favorites, settings
```

`:core:*` and `:source:*` contain no UI code and never import Compose — enforced by the
build, not by convention, so an Android TV or desktop frontend can consume them unchanged.
See [`docs/PLAN.md`](docs/PLAN.md) §2.

## Documentation

| Document | What it is |
|---|---|
| [`docs/FREEZE.md`](docs/FREEZE.md) | The canonical, frozen scope for v1.0. Read this first. |
| [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md) | Definition of done, as numbered binary criteria |
| [`docs/PLAN.md`](docs/PLAN.md) | Stack, module structure, milestones |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to contribute, and what will get you banned |
| [`SECURITY.md`](SECURITY.md) | Private vulnerability disclosure |

## Licence

GPLv3-or-later. See [`LICENSE`](LICENSE).

Vibrato is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version. It is distributed in the hope that
it will be useful, but **without any warranty**; without even the implied warranty of
merchantability or fitness for a particular purpose.
