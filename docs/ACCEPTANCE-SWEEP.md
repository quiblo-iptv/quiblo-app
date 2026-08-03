<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Acceptance Sweep — v1.0.0

The Definition of Done in [`ACCEPTANCE.md`](ACCEPTANCE.md) requires every criterion to pass
on a physical Android 11 device and a physical Android 14 device, with both an M3U and an
Xtream source configured, on a fresh install and on an upgrade.

This file records what has been verified so far, how, and what is left. It is the gate on
tagging v1.0.0 — do not tag while anything in §5 or §6 is unchecked.

**Last updated:** 2026-08-03, at commit `7dd52c2`.

> **The recorded results predate the player and browse work that followed.** Everything in
> §2 and §3 was measured at `3494da5`. Since then the player gained aspect modes, gesture
> controls and full-screen; browse moved to poster grids and grid-by-default; and movies
> and series gained detail screens. Cold start and the parser results are unaffected by
> any of that, but **AC-PLAY-\*, AC-PL-05 and the scroll-jank baseline want re-running**,
> and the movie screen adds a network call the sweep never exercised (see §6).

**Devices used so far:**

| Device | OS | Notes |
|---|---|---|
| Lenovo TB305XU | Android 15 (API 35), arm64, 3.7 GB RAM | Neither DoD row. Low-RAM mid-range, so a fair cold-start target |
| `quiblo_api30` emulator | **Android 11 (API 30)**, Pixel 5, x86_64 | The minSdk row, but an emulator — does not satisfy the DoD's "physical device" |
| Pixel Tablet emulator | Android 15 | Desktop-fast; treat its numbers as upper bounds only |

**Android 14 has not been tested at all, and Android 11 only on an emulator.** Both DoD
rows are still open.

**Since `FREEZE.md` Amendment 1 (2026-08-03) the television is part of v1.0**, so this sweep
now has a third target: the Haier MatrixTV EE (Google TV, Android 14) recorded in
`PLAN-TV.md` §0. None of the AC-TV-\* criteria in `PLAN-TV.md` §6 have been run, because the
TV app does not exist yet. v1.0.0 cannot be tagged on a green phone sweep alone.

---

## 1. Verified mechanically

These are enforced by the build and re-checked on every CI run. They do not need repeating
by hand on the devices.

| ID | Result | Evidence |
|---|---|---|
| AC-NFR-02 | **Pass** — 4.87 MB against a 25 MB budget | `assembleRelease`; the release workflow also fails above the budget |
| AC-NFR-05 | **Pass** | `./gradlew build detektAll coverageAll lint` green, 2026-08-03 |
| AC-NFR-06 | **Pass** | `enforceNoCompose()` in `quiblo.jvm.library`; no `:core:*`/`:source:*` build file references Compose or a feature module |
| AC-NFR-07 | **Pass** — `:source:m3u` 98.0%, `:source:xtream` 83.9% | `./gradlew coverageAll`, threshold 80 |
| AC-LEGAL-01 | **Pass** | `LICENSE` is the unmodified GPLv3 text |
| AC-LEGAL-02 | **Pass** — 81/81 `.kt` files | header check across `app core source feature build-logic` |
| AC-LEGAL-04 | **Pass** | CI greps for provider URLs and the forbidden brand string; all test payloads are synthetic |
| AC-LEGAL-05 | **Pass** | README "Quiblo supplies no content" |

## 2. Verified on hardware (Lenovo TB305XU, Android 15)

Release build, R8-minified, signed with a throwaway debug key for testing — **not the
shipping artifact**, so these need one confirming pass against the real signed APK.

Driven against a synthetic 20,000-entry M3U from [`tools/gen_playlist.py`](../tools/gen_playlist.py),
served to the device over `adb reverse` so no network is involved:

```sh
python tools/gen_playlist.py 20000 big.m3u
python -m http.server 8000 --bind 127.0.0.1      # in the directory holding big.m3u
adb reverse tcp:8000 tcp:8000                     # then add http://127.0.0.1:8000/big.m3u
```

Every host in it is under `.invalid` (RFC 2606), so nothing resolves and nothing plays: the
playlist exercises ingestion, storage and browse, never playback.

| ID | Result | Evidence |
|---|---|---|
| AC-PL-01 | **Pass** (qualified) | 20,002 channels ingested and grouped, ~4s end to end. Served over a loopback tunnel, so this says nothing about the 10 Mbps clause |
| AC-PL-04 | **Pass** | BOM, CRLF, an unescaped comma in a display name, a missing `group-title` and a truncated final line all handled; "Loaded 20002 channels. 2 entries could not be read and were skipped" surfaced to the user |
| AC-PL-05 | **Pass** | 20k list scrolls; see the jank note below |
| AC-NFR-01 | **Pass** | See below |
| AC-NFR-08 (RTL half) | **Pass** | Under `ar-EG` the whole UI mirrors: nav order reverses, rows and icons flip, the category chip right-aligns |
| AC-NFR-09 | **Pass** | Dark and light both render correctly and the app follows the system setting live, without a restart |
| AC-FAV (survives refresh) | **Pass** | Three favourites set, source fully re-ingested, all three still present and still flagged in the browse list |

**AC-NFR-01 — cold start, populated database.** 20,002 channels in the database, six runs
after `force-stop`:

| run | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| TotalTime (ms) | 471 | 450 | 438 | 439 | 441 | 449 |

**Median 445 ms against a 2000 ms budget.** Loading 20k channels costs nothing at startup,
which matches the architecture: Koin starts, Room is built lazily, and nothing queries the
database before the first frame.

Two earlier readings that looked alarming were both measurement artefacts, recorded here so
nobody re-derives them:

- The **first** launch after install took 1590 ms. That is one-time dexopt and ART warmup,
  not steady state — every subsequent run sat near 445 ms.
- The debug build against a populated database took ~1850 ms. That is unminified-build
  overhead, not data volume. The release figures above settle it.

A baseline profile remains ungenerated and, at 4.5x headroom, unnecessary.

**Scroll jank, worth watching.** Six hard flings through the 20k list: 118 frames, 5.93%
janky, 50th percentile 9 ms, 90th 28 ms, 95th 36 ms. The 90th and 95th percentiles are over
the 16.7 ms frame budget. No criterion sets a jank threshold so nothing fails, but flinging
a 20k list is exactly the risk PLAN.md §5 calls out, and this is the number to compare
against after any change to the browse list.

## 3. Verified on Android 11 (API 30 emulator)

The minSdk row. An emulator, so it does not satisfy the DoD, but it is where OS-version
breakage would show — scoped storage, SAF, and the permission model all changed after 11.
**Nothing behaved differently from Android 15.**

| ID | Result | Evidence |
|---|---|---|
| AC-PL-01, AC-PL-04 | **Pass** | Byte-identical outcome to Android 15: "Loaded 20002 channels. 2 entries could not be read and were skipped" |
| AC-NFR-01 | **Pass** | 20,002 channels loaded: 329 / 350 / 462 / 328 / 327 / 323 ms, **median 328 ms** |
| AC-DATA (export) | **Pass** | SAF create-document opens, defaults to `quiblo-backup.json` in Downloads, writes valid JSON with `schema_version: 1` and snake_case keys |
| AC-DATA (import, valid) | **Pass** | Re-importing the app's own export is idempotent: "Nothing to restore — everything in that file is already set up" |
| AC-DATA (import, rejection) | **Pass** | A hand-edited `schema_version: 99` file is refused with "That backup was written by a newer version of Quiblo (format 99, this build reads 1). Update the app and try again" — it names both versions rather than failing vaguely |
| AC-LEGAL-03 | **Pass** | Settings carries an "Open source licenses" section with a Show licenses screen |

The backup copy states, on screen, that "Passwords are never written to the file — you will
re-enter them after importing", and the exported JSON contains no credential field. That is
the AC-XT-04 posture holding across the export path, though it wants one more check against
an actual Xtream source once an account is available.

**Driving note for whoever repeats this.** In the SAF picker, `input tap` silently does
nothing on a file row — the tap lands but no selection occurs. `KEYCODE_DPAD_DOWN` then
`KEYCODE_ENTER` works. That cost twenty minutes to find; do not assume a failed tap means a
broken app.

## 4. Emulator baseline

Superseded by §2 for anything the tablet covered; kept as a comparison point. Release
build, Pixel Tablet emulator, Android 15, **empty** database: 462 / 663 / 499 / 678 / 538 /
568 ms, median 553 ms.

Note that the emulator on an empty database was *slower* than the tablet on 20,002 channels.
That is the clearest evidence that database size does not touch startup cost here.

## 5. Requires physical devices

Nothing below has been executed. Run each on **Android 11** and **Android 14**, with an M3U
source and an Xtream source configured, on a fresh install and again over an upgrade.

- **Playlists** — AC-PL-02, AC-PL-03, AC-PL-06, AC-PL-07 (01/04/05 covered in §2 on Android 15)
- **Xtream** — AC-XT-01 … AC-XT-06
- **Guide** — AC-EPG-01 … AC-EPG-05
- **Playback** — AC-PLAY-01 … AC-PLAY-10
- **Favourites** — AC-FAV-01 … AC-FAV-05 (survival across refresh covered in §2)
- **Export / import** — AC-DATA-01 … AC-DATA-05 covered on Android 11 in §3; needs a
  confirming pass on hardware, and one round-trip carrying an Xtream source
- **Cold start** — AC-NFR-01, to confirm §2 on the two DoD OS versions
- **No unconfigured network traffic** — AC-NFR-03, by packet capture on a clean install
- **Permissions** — AC-NFR-04, see §6
- **Strings** — AC-NFR-08; the RTL half is covered in §2, but no screen has been read for
  hardcoded strings on-device
- **Licences screen** — AC-LEGAL-03
- **Playback** — needs a playlist of real streams. The synthetic playlist used in §2 points
  every entry at `.invalid`, so it cannot exercise the player at all

Note that AC-PL-07 and the Xtream criteria cannot currently be exercised against the
account previously used for testing: that panel is API-blocked at the provider's end and
returns 469 regardless of client behaviour. A different Xtream account is needed for the
sweep.

The "upgrade from the previous release" half of the Definition of Done cannot apply to
v1.0.0, since there is no previous release to upgrade from. It becomes live at v1.0.1.

## 6. Open — needs a decision before tagging

**Requests to the panel, and the cost of each.** Recorded here because getting an account
throttled is the failure mode this project keeps meeting, and because it is not obvious
from the code which actions cost anything.

| Action | Requests |
|---|---|
| Add a source, or the manual refresh button | auth + 6 catalogue calls |
| Anything else on a browse screen | none — favouriting, scrolling and filtering are local |
| A live row scrolling into view, in list mode | one `get_short_epg`, once ever per channel, rate-limited and skipped when cached |
| Opening a series | one `get_series_info`, **uncached** — re-opening the same series fetches again |
| Opening a movie | one `get_vod_info`, **uncached**, added at `a5de4ee` |

Nothing refreshes automatically: not on launch, not on tab switch, not on scroll.

`XtreamSource` stops asking for fifteen minutes after a panel refuses it, across all four
call paths. Before `a5de4ee` that backoff existed only around the guide, so a blocked
account kept being asked by the catalogue, series and film paths — which is how a short
block becomes a lasting one.

**The obvious remaining reduction is caching series and film details for the session**, so
re-opening the same item costs nothing. Not done yet.

**AC-NFR-04 does not pass as written.** The criterion says "Permissions requested: INTERNET
and network state only." The merged release manifest contains four:

```
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.WAKE_LOCK                              ← from Media3
dev.quiblo.player.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION ← from androidx.core
```

Neither extra is declared by this project; both arrive transitively. `WAKE_LOCK` is what
stops the device sleeping mid-playback, and the androidx one is a self-scoped signature
permission that is not user-visible at all. The spirit of the criterion — no storage, no
location, no contacts — holds.

So this is a wording problem rather than a defect, but it is a criterion that currently
reads as failing, and that needs resolving deliberately rather than by ignoring it. Two
options:

1. **Amend AC-NFR-04** to permit permissions contributed by dependencies, and name these
   two explicitly so a third one cannot arrive unnoticed.
2. **Strip them** with `tools:node="remove"`. Cheap for the androidx permission. Not
   advisable for `WAKE_LOCK` — removing it risks the screen sleeping during playback, which
   trades a documentation problem for a real bug.

Option 1 is the recommendation. Either way it is a scope decision, and `FREEZE.md` requires
scope decisions to be dated amendments rather than quiet edits.
