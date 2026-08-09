<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

<div align="center">

# Quiblo

**A free, open source IPTV player for Android phones and TV. Bring your own playlist.**

[![Release](https://img.shields.io/github/v/release/quiblo-iptv/quiblo-app?include_prereleases&sort=semver&label=release&color=3DDC84)](https://github.com/quiblo-iptv/quiblo-app/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/quiblo-iptv/quiblo-app/total?label=downloads&color=blue)](https://github.com/quiblo-iptv/quiblo-app/releases)
[![Stars](https://img.shields.io/github/stars/quiblo-iptv/quiblo-app?label=stars&color=FFCA28)](https://github.com/quiblo-iptv/quiblo-app/stargazers)
[![CI](https://github.com/quiblo-iptv/quiblo-app/actions/workflows/ci.yml/badge.svg)](https://github.com/quiblo-iptv/quiblo-app/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

[**Read the wiki**](https://quiblo-iptv.github.io/quiblo-wiki/) · [**Download**](https://github.com/quiblo-iptv/quiblo-app/releases/latest) · [**How it is built**](#learn-from-this-repository) · [**Support the project**](#support-quiblo)

</div>

---

Quiblo plays Live TV, movies and series from playlists **you** supply — an M3U/M3U8 URL or
file, or an Xtream Codes account. No ads, no accounts, no tracking, no backend, no server of
ours anywhere in the path.

## Built with

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3_ExoPlayer-1.10.1-FF6F00?logo=android&logoColor=white)](https://developer.android.com/media/media3)
[![Room](https://img.shields.io/badge/Room-2.8.4-003B57?logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Ktor](https://img.shields.io/badge/Ktor-3.5.2-087CFA?logo=ktor&logoColor=white)](https://ktor.io)
[![Koin](https://img.shields.io/badge/Koin-4.2.2-F5A623)](https://insert-koin.io)
[![Gradle](https://img.shields.io/badge/Gradle-9.6.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![Android](https://img.shields.io/badge/Android-11+_(API_30)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Android TV](https://img.shields.io/badge/Android_TV-Google_TV-3DDC84?logo=androidtv&logoColor=white)](https://developer.android.com/tv)

## Install

Grab the APK from [**the latest release**](https://github.com/quiblo-iptv/quiblo-app/releases/latest).
**There are two, and they are not interchangeable:**

| File | Install it on |
|---|---|
| `quiblo-<version>.apk` | Phones and tablets |
| `quiblo-tv-<version>.apk` | Android TV and Google TV |

The phone APK installs happily on a television and then never appears in its launcher —
only the TV build declares a leanback launcher entry. Both are signed with the same key and
have separate application ids, so you can have both on one device.

Every release also ships a `.sha256` beside each APK. Verify before installing:

```bash
sha256sum -c quiblo-<version>.apk.sha256
```

Then: open the app, add your M3U URL or Xtream credentials, and browse. There is no account
step and no setup wizard.

> **Status: alpha.** Sources, playback, browse, favourites, Xtream, EPG, profiles,
> export/import and release engineering are all built. What stands between this and v1.0.0
> is the acceptance sweep on physical devices — not a formality, since the bugs that have
> mattered most were every one of them found on hardware. The road there is
> [`agile/006`](agile/006%20First%20Beat%20of%20Quiblo%20—%20the%20road%20to%201.0.0.md).

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
| **Layout & Search** | Poster grids for movies and series, category filter, list/grid toggle, expandable search. On the television, one search across live channels, films and series at once, filterable by genre |
| **Theme & Design** | Material 3 Expressive, dark and light |
| **Resume** | Playback position remembered per item, and per episode within a series |
| **Favourites** | Survive playlist refresh |
| **Profiles** | Choose who is watching at launch. Favourites and continue watching are kept per person; playlists and settings are shared. Guest keeps nothing |
| **Film information** | Optional descriptions, genres, certificates, ratings, director and cast from The Movie Database, using your own API key. Off by default. A one-off scan in Settings can describe the whole catalogue at once, so genres and scores are there before you browse |
| **Backup** | Versioned export and import of sources and favourites, to a file you choose |
| **Storage & Privacy** | Entirely on-device with zero telemetry |

Not yet implemented, in rough priority order: an EPG time-grid, a quick-zap overlay,
catchup/timeshift, and picture-in-picture. Several are Phase 2 in
[`docs/PLAN.md`](docs/PLAN.md) §6. Desktop, browser and the other TV platforms are planned
in [`agile/007`](agile/007%20Version%202%20of%20Quiblo%20—%20the%20platforms%20beyond%20Android.md).

## Learn from this repository

**This repo is meant to be read, not only run.** Most open-source apps ship the code and
keep the reasoning in someone's head. Here the reasoning is the larger half of the
repository, and it is all in the open — including the parts that went wrong.

**If you are learning Android**, the useful thing here is not the feature list, it is the
seams. `:core:*` and `:source:*` contain no UI code and never import Compose, and that is
enforced by the build rather than by convention — which is exactly why a whole television
frontend could be added as a presentation layer over the same ViewModels instead of a fork.
Read [`docs/PLAN.md`](docs/PLAN.md) §2 for the module structure, then
[`docs/PLAN-TV.md`](docs/PLAN-TV.md) to watch that claim get tested.

**If you are interested in agentic coding**, this app was built with Claude Code, and the
honest account is in [`agile/`](agile). It is not a demo of prompting. It is eleven dated
rounds including the failures, because those are the part worth reading:

- A cache that stored **failures as answers** — every metadata error became "this title
  matches nothing", cached for a fortnight ([`agile/005`](agile/005%20Feature%20Round%20of%20Quiblo%20—%20Search,%20Scan%20and%20Profiles.md)).
- A rate limiter that ran at **exactly twice its documented rate** for weeks, with a passing
  test — because the test measured one request's wait instead of the sustained rate.
- A dependency-injection module that compiled, passed detekt, passed lint, passed every unit
  test, and **took the app down on the screen that needed it**.
- **Nine features nobody could reach**, deleted rather than kept.
- A UI shake that took **four wrong answers** before the real cause turned up.
- **Two releases published that contained nothing** — `0.2.1` and `0.2.2`, where `0.2.2` is
  the same application as `0.2.1` with a different number, because "a merge to main is a
  release" had no opinion about *what* had merged. The fix reads the Conventional Commit
  types the project was already writing ([`docs/RELEASE-MANAGEMENT.md`](docs/RELEASE-MANAGEMENT.md) §6).

The through-line: the leverage was real, and the verification was never optional.

**If you want the full specification**, it is here in full — scope, criteria, and what has
actually been verified as opposed to assumed:

| Read this | For |
|---|---|
| [`docs/FREEZE.md`](docs/FREEZE.md) | The frozen scope, with six dated amendments arguing every change to it |
| [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md) | Definition of done, as numbered binary criteria |
| [`docs/ACCEPTANCE-SWEEP.md`](docs/ACCEPTANCE-SWEEP.md) | What is genuinely verified on hardware — and what is not |
| [`docs/RELEASE-MANAGEMENT.md`](docs/RELEASE-MANAGEMENT.md) | What a version number means here, and what alpha, beta and RC each promise |
| [`agile/`](agile) | Every round, including the bugs and what they taught |

The [**wiki**](https://quiblo-iptv.github.io/quiblo-wiki/) is the readable version of all of
it, with a searchable code reference.

## Privacy

Quiblo has no telemetry, no analytics, no crash-reporting SDK, and no update check
against any project-controlled server. The only outbound connections are to the hosts you
configure yourself — your playlist or Xtream provider, and The Movie Database only if you
have entered your own API key for it. Quiblo ships no key of its own, so with the setting
untouched nothing ever contacts that service. This is verifiable by packet capture on a
clean install, and is a release criterion (AC-NFR-03).

Permissions requested: `INTERNET` and `ACCESS_NETWORK_STATE`. Nothing else — file access
goes through the system document picker, so no storage permission is needed.

Xtream credentials are stored encrypted on-device and are never written to logs, export
files, or crash traces.

## Building

Requires **JDK 17 or 21** and the **Android SDK** (platform 37, build-tools 37.0.0).
Android Studio is not required.

```bash
git clone https://github.com/quiblo-iptv/quiblo-app.git
cd quiblo-app
./gradlew build detekt lint     # the same gate CI runs
./gradlew :app:installDebug     # phone and tablet
./gradlew :app-tv:installDebug  # Android TV / Google TV
```

Minimum supported device: **Android 11 (API 30)**.

> **If `detekt` fails with a bare version number** such as `> 25.0.4`, check for
> `gradle/gradle-daemon-jvm.properties`. Android Studio can generate it pinning the daemon
> to JDK 25, which detekt cannot run on. Deleting it fixes the build; nothing is wrong with
> the code.

**There are two apps, and a release ships both APKs.** `:app` is the phone and tablet build
(`dev.quiblo.player`); `:app-tv` is the television build (`dev.quiblo.tv`), with a D-pad
interface and a leanback launcher entry. They are separate application ids, so both can be
installed at once, and they share every layer below the UI.

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

## Contributing

Bug reports and pull requests are welcome. Start with
[`CONTRIBUTING.md`](CONTRIBUTING.md), and read [`docs/FREEZE.md`](docs/FREEZE.md) before
proposing a feature — the scope is deliberately frozen, and a change outside it needs an
amendment rather than an argument.

| Document | What it is |
|---|---|
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | How to contribute, and what will get you banned |
| [`docs/RELEASING.md`](docs/RELEASING.md) | Signing key handling and the release process |
| [`SECURITY.md`](SECURITY.md) | Private vulnerability disclosure |
| [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) | How we treat each other |

## Support Quiblo

Quiblo is free software and always will be. If it is useful to you and you are able,
[**sponsoring the project**](https://github.com/sponsors/quiblo-iptv) helps — but please read
what that does and does not buy first, because we would rather be clear than funded:

- **No feature is ever paywalled.** Not now, not on any future platform. If it exists, it is
  in the free build for everyone.
- **No sponsor influences what gets built.** Priorities come from the frozen scope and the
  issue tracker, and nowhere else.
- **No sponsor logo goes in the app.** Quiblo makes no network call to any host you did not
  configure, and a logo fetched at runtime would break the one promise this project cares
  most about keeping.

**What the money actually goes to**, named plainly rather than as an appeal: a second
television and an Android 14 phone to finish the acceptance sweep on real hardware, an
Xtream account to test against, and the developer accounts needed to bring Quiblo to
Samsung and LG televisions.

Not able to sponsor? These help just as much: star the repo, file a good bug report, test a
release on a device we do not have, or fix something in the wiki.

## Licence

GPLv3-or-later. See [`LICENSE`](LICENSE).

Quiblo is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3
of the License, or (at your option) any later version. It is distributed in the hope that
it will be useful, but **without any warranty**; without even the implied warranty of
merchantability or fitness for a particular purpose.
