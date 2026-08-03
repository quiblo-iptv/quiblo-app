# Acceptance Criteria — Vibrato v1.0

Every criterion is binary: it passes or it does not. "Mostly works" is a fail.
IDs are stable — reference them in commits and issues (`fix: AC-PL-04 crash on BOM-prefixed M3U`).

---

## AC-PL — Playlist: M3U

| ID | Given / When / Then |
|---|---|
| AC-PL-01 | Given a valid remote M3U URL, when the user adds it, then channels appear grouped by `group-title` within 5s on a 10 Mbps connection. |
| AC-PL-02 | Given a local `.m3u`/`.m3u8` file picked via SAF, when imported, then it parses identically to the remote path. |
| AC-PL-03 | Given entries with `tvg-logo`, when the list renders, then logos load lazily and a placeholder shows on failure. Broken logos never block the row. |
| AC-PL-04 | Given a malformed playlist (UTF-8 BOM, CRLF, missing `#EXTM3U`, truncated final line, unescaped commas in the display name), when parsed, then valid entries load and invalid lines are skipped with a count surfaced to the user. No crash. |
| AC-PL-05 | Given a playlist of 20,000 entries, when opened, then the list scrolls at 60fps and initial render completes under 3s. |
| AC-PL-06 | Given an entry with no `group-title`, when rendered, then it appears under a single "Ungrouped" category. |
| AC-PL-07 | Given a URL that returns 404, times out, or serves HTML instead of a playlist, when added, then a specific, human-readable error is shown. Never a raw stack trace or a bare "Error". |

## AC-XT — Playlist: Xtream Codes

| ID | Given / When / Then |
|---|---|
| AC-XT-01 | Given a base URL, username, and password, when the user authenticates, then live categories, VOD, and series populate. |
| AC-XT-02 | Given wrong credentials, when submitted, then an auth-specific error appears, distinguished from a network error. |
| AC-XT-03 | Given a base URL entered with or without scheme, with or without port, with or without a trailing slash, when submitted, then all normalise to the same working endpoint. |
| AC-XT-04 | Given a successful login, when credentials are persisted, then they are stored encrypted and appear in no log output, no export file, and no crash trace. |
| AC-XT-05 | Given an account whose subscription has expired, when the user opens the app, then the expiry is surfaced clearly rather than presenting as a generic playback failure. |
| AC-XT-06 | Given the API returns fields as strings where numbers are expected (a common panel quirk), when deserialised, then parsing succeeds. |

## AC-EPG — Guide

| ID | Given / When / Then |
|---|---|
| AC-EPG-01 | Given an Xtream source, when a channel row renders, then the current programme title and a progress bar for elapsed time are shown. |
| AC-EPG-02 | Given a channel is selected, when the user opens its detail, then now/next programmes with start and end times are listed. |
| AC-EPG-03 | Given EPG timestamps in a non-local timezone with offsets, when displayed, then times render correctly in the device's local timezone. |
| AC-EPG-04 | Given an M3U source, when a channel row renders, then no guide UI appears and no empty or broken placeholder is shown. |
| AC-EPG-05 | Given cached EPG data and no network, when the app opens, then the cached guide still renders. |

## AC-PLAY — Playback

| ID | Given / When / Then |
|---|---|
| AC-PLAY-01 | Given an HLS live stream, when selected, then playback begins within 3s on a 10 Mbps connection. |
| AC-PLAY-02 | Given a raw MPEG-TS stream, when selected, then it plays with no seek bar shown (correctly reflecting that it is unseekable). |
| AC-PLAY-03 | Given a VOD `.mp4` or `.mkv`, when played, then seek, pause, and resume all work, and playback position is remembered on reopen. |
| AC-PLAY-04 | Given a stream with multiple audio tracks or subtitle tracks, when playing, then the user can switch between them. |
| AC-PLAY-05 | Given a dead or unreachable stream URL, when selected, then a clear error appears within 15s. The app does not hang indefinitely. |
| AC-PLAY-06 | Given a stream drops mid-playback, when the connection returns, then the player retries automatically at least 3 times with backoff before surfacing an error. |
| AC-PLAY-07 | Given playback is active, when the user rotates the device, then playback continues without re-buffering from zero. |
| AC-PLAY-08 | Given playback is active, when an incoming call or another app takes audio focus, then playback pauses and resumes appropriately. |
| AC-PLAY-09 | Given the player is open, when the user backgrounds the app, then video playback stops and no audio leaks (no background playback in v1). |
| AC-PLAY-10 | Given full-screen playback, when the user taps once, then controls appear and auto-hide after 3s of inactivity. |

## AC-FAV — Favourites and Groups

| ID | Given / When / Then |
|---|---|
| AC-FAV-01 | Given any live channel, VOD item, or series, when the user marks it favourite, then it appears in a dedicated Favourites section. |
| AC-FAV-02 | Given a favourite, when the app is killed and reopened, then the favourite persists. |
| AC-FAV-03 | Given a playlist is refreshed and a favourited channel's URL changed but its `tvg-id`/stream ID is the same, when the refresh completes, then the favourite survives. |
| AC-FAV-04 | Given multiple categories, when the user browses, then they can filter to a single category and search by name within it. |
| AC-FAV-05 | Given a search query, when typed, then results filter within 200ms across a 20,000-entry list. |

## AC-DATA — Storage, Export, Import

| ID | Given / When / Then |
|---|---|
| AC-DATA-01 | Given a configured app, when the user exports, then a single file containing sources, favourites, and settings is written via SAF to a user-chosen location. |
| AC-DATA-02 | Given an export file, when imported on a fresh install, then sources, favourites, and settings are fully restored. |
| AC-DATA-03 | Given an export, when inspected, then Xtream passwords are either omitted or encrypted — never plaintext. |
| AC-DATA-04 | Given an export file from a future schema version, when imported, then it is rejected with a clear version-mismatch message rather than corrupting state. |
| AC-DATA-05 | Given no network, when the app opens, then the last-known channel list renders from local cache. |

## AC-NFR — Non-Functional

| ID | Criterion |
|---|---|
| AC-NFR-01 | Cold start to interactive: under 2s on a mid-range Android 11 device. |
| AC-NFR-02 | Release APK under 25 MB. |
| AC-NFR-03 | Zero network requests to any host the user did not configure. Verifiable by packet capture on a clean install. A metadata service the user has enabled by entering their own key counts as configured; with no key entered, a packet capture must show no contact with it at all. |
| AC-NFR-04 | Permissions requested: INTERNET and network state only. No storage permission (SAF instead), no location, no contacts. |
| AC-NFR-05 | Passes `./gradlew lint detekt test` with zero errors in CI. |
| AC-NFR-06 | `:core:*` modules have no dependency on Compose or any `:feature:*` module — enforced by a build-level dependency check, not convention. |
| AC-NFR-07 | Parser modules (M3U, Xtream) have unit test coverage above 80%, including the malformed-input cases in AC-PL-04. |
| AC-NFR-08 | Full RTL layout support and no hardcoded user-facing strings outside `strings.xml`. |
| AC-NFR-09 | Dark theme and light theme both render correctly; the app respects the system setting. |

## AC-LEGAL — Licensing and Compliance

| ID | Criterion |
|---|---|
| AC-LEGAL-01 | `LICENSE` contains the full, unmodified GPLv3 text. |
| AC-LEGAL-02 | Every source file carries a GPLv3 header. |
| AC-LEGAL-03 | An in-app "Open Source Licenses" screen lists all third-party dependencies and their licenses. |
| AC-LEGAL-04 | The repository ships no playlist, no provider URL, no credentials, and no reference to where any of these may be obtained — including in tests, fixtures, issues, and documentation. Test fixtures use synthetic data only. |
| AC-LEGAL-05 | The README states the app supplies no content and that users are responsible for the legality of sources they configure. |

---

## Definition of Done for v1.0

Every AC above passes on a physical Android 11 device and a physical Android 14 device, with both an M3U source and an Xtream source configured, verified against a fresh install and against an upgrade from the previous release.
