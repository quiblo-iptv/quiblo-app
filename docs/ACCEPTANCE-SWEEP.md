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

**Last updated:** 2026-08-03, at commit `697eca7`.

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

## 2. Verified on the emulator only

Real evidence, but not against the criterion as written. Repeat these on hardware.

**AC-NFR-01 — cold start under 2s.** Release build (R8, signed with a throwaway debug key
for measurement only), `emulator-5554`, Android 15, fresh install with no sources
configured:

| run | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| `am start -W` TotalTime (ms) | 462 | 663 | 499 | 678 | 538 | 568 |

Median 553 ms, worst 678 ms. The startup path is clean by construction: `VibratoApplication`
starts Koin and nothing else, Room is built lazily, and there is no main-thread query before
the first frame.

**Two caveats, both of which the hardware sweep must settle:**

1. A desktop emulator is much faster than the mid-range Android 11 device the criterion
   names. Treat 553 ms as an upper bound on the code's own cost, not as a passing result.
2. This was measured on an **empty** database. The debug build against a populated 26 MB
   database took ~1850 ms, but that number confounds two variables — it is also an
   unminified build — so it does not transfer. Getting a populated *release* database on
   this machine was not possible: the emulator is a Play image with no root, and the backup
   format carries sources and favourites rather than the channel corpus, so import cannot
   seed one. **Measure cold start on hardware with a large playlist loaded**, not on a fresh
   install; that is the number the criterion is actually about.

A baseline profile has not been generated. Given the headroom it is an optimisation rather
than a blocker, but it is the obvious lever if the hardware number comes in near 2s.

## 3. Requires physical devices

Nothing below has been executed. Run each on **Android 11** and **Android 14**, with an M3U
source and an Xtream source configured, on a fresh install and again over an upgrade.

- **Playlists** — AC-PL-01 … AC-PL-07
- **Xtream** — AC-XT-01 … AC-XT-06
- **Guide** — AC-EPG-01 … AC-EPG-05
- **Playback** — AC-PLAY-01 … AC-PLAY-10
- **Favourites** — AC-FAV-01 … AC-FAV-05
- **Export / import** — AC-DATA-01 … AC-DATA-05
- **Cold start on hardware** — AC-NFR-01, per the caveats in §2
- **No unconfigured network traffic** — AC-NFR-03, by packet capture on a clean install
- **Permissions** — AC-NFR-04, see §4
- **RTL and strings** — AC-NFR-08
- **Dark and light** — AC-NFR-09
- **Licences screen** — AC-LEGAL-03

Note that AC-PL-07 and the Xtream criteria cannot currently be exercised against the
account previously used for testing: that panel is API-blocked at the provider's end and
returns 469 regardless of client behaviour. A different Xtream account is needed for the
sweep.

The "upgrade from the previous release" half of the Definition of Done cannot apply to
v1.0.0, since there is no previous release to upgrade from. It becomes live at v1.0.1.

## 4. Open — needs a decision before tagging

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
