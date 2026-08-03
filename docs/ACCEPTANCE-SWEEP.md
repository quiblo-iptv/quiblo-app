<!--
  Vibrato — a free, open source IPTV player.
  Copyright (C) 2026 The Vibrato Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Acceptance Sweep — v1.0.0

The Definition of Done in [`ACCEPTANCE.md`](ACCEPTANCE.md) requires every criterion to pass
on a physical Android 11 device and a physical Android 14 device, with both an M3U and an
Xtream source configured, on a fresh install and on an upgrade.

This file records what has been verified so far, how, and what is left. It is the gate on
tagging v1.0.0 — do not tag while anything in §3 is unchecked.

**Last updated:** 2026-08-03, at commit `6d5b4e8`.

**Devices used so far:**

| Device | OS | Notes |
|---|---|---|
| Lenovo TB305XU | Android 15 (API 35), arm64, 3.7 GB RAM | Neither DoD row. Low-RAM mid-range, so a fair cold-start target |
| Pixel Tablet emulator | Android 15 | Desktop-fast; treat its numbers as upper bounds only |

**Neither Android 11 nor Android 14 has been tested at all.** Both DoD rows are still open.

---

## 1. Verified mechanically

These are enforced by the build and re-checked on every CI run. They do not need repeating
by hand on the devices.

| ID | Result | Evidence |
|---|---|---|
| AC-NFR-02 | **Pass** — 4.87 MB against a 25 MB budget | `assembleRelease`; the release workflow also fails above the budget |
| AC-NFR-05 | **Pass** | `./gradlew build detektAll coverageAll lint` green, 2026-08-03 |
| AC-NFR-06 | **Pass** | `enforceNoCompose()` in `vibrato.jvm.library`; no `:core:*`/`:source:*` build file references Compose or a feature module |
| AC-NFR-07 | **Pass** — `:source:m3u` 98.0%, `:source:xtream` 83.9% | `./gradlew coverageAll`, threshold 80 |
| AC-LEGAL-01 | **Pass** | `LICENSE` is the unmodified GPLv3 text |
| AC-LEGAL-02 | **Pass** — 81/81 `.kt` files | header check across `app core source feature build-logic` |
| AC-LEGAL-04 | **Pass** | CI greps for provider URLs and the forbidden brand string; all test payloads are synthetic |
| AC-LEGAL-05 | **Pass** | README "Vibrato supplies no content" |

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

## 3. Emulator baseline

Superseded by §2 for anything the tablet covered; kept as a comparison point. Release
build, Pixel Tablet emulator, Android 15, **empty** database: 462 / 663 / 499 / 678 / 538 /
568 ms, median 553 ms.

Note that the emulator on an empty database was *slower* than the tablet on 20,002 channels.
That is the clearest evidence that database size does not touch startup cost here.

## 4. Requires physical devices

Nothing below has been executed. Run each on **Android 11** and **Android 14**, with an M3U
source and an Xtream source configured, on a fresh install and again over an upgrade.

- **Playlists** — AC-PL-02, AC-PL-03, AC-PL-06, AC-PL-07 (01/04/05 covered in §2 on Android 15)
- **Xtream** — AC-XT-01 … AC-XT-06
- **Guide** — AC-EPG-01 … AC-EPG-05
- **Playback** — AC-PLAY-01 … AC-PLAY-10
- **Favourites** — AC-FAV-01 … AC-FAV-05 (survival across refresh covered in §2)
- **Export / import** — AC-DATA-01 … AC-DATA-05 — **not started.** Both directions go
  through the SAF file picker, which is the one flow that resists adb driving
- **Cold start** — AC-NFR-01, to confirm §2 on the two DoD OS versions
- **No unconfigured network traffic** — AC-NFR-03, by packet capture on a clean install
- **Permissions** — AC-NFR-04, see §5
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

## 5. Open — needs a decision before tagging

**AC-NFR-04 does not pass as written.** The criterion says "Permissions requested: INTERNET
and network state only." The merged release manifest contains four:

```
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.WAKE_LOCK                              ← from Media3
dev.vibrato.player.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION ← from androidx.core
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
