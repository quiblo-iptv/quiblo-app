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
tagging v1.0.0 — do not tag while anything in §5, §6 or §7 is unchecked.

**Last updated:** 2026-08-04, after the `agile/001` bug fixes.

> **Since `ebfa928` the data layer and the whole television frontend have changed
> materially**, and two recorded results are now stale in a way that matters:
>
> - **AC-NFR-01 and the scroll-jank baseline were measured before the browse query was
>   indexed and before its mapping left the main thread.** Schema 10 added
>   `(sourceId, kind, sortIndex)` to `channels`, so the first launch after upgrading builds
>   an index over the whole playlist — a one-off cost the old cold-start figures do not
>   include, and a saving every emission after it does not either. **Re-measure both.**
> - **AC-NFR-02 wants re-running**: the phone APK is 5.32 MB minified at the time of
>   writing, against 5.47 MB recorded below, and the television has gained four screens.
>
> The parser results in §2 and §3 are unaffected — nothing in that path changed.

> **The recorded results predate almost everything built since.** Everything in §2 and §3
> was measured at `3494da5`. Since then the player gained aspect modes, gesture controls
> and full-screen; browse moved to poster grids and grid-by-default; movies and series
> gained detail screens; the project was renamed; DASH and optional TMDB metadata arrived;
> the theme became settable; and **the whole `:app-tv` television frontend was built**
> (T0–T4, `a4e9845`…`e317475`). Cold start and the parser results are unaffected by any of
> that, but **AC-PLAY-\*, AC-PL-05 and the scroll-jank baseline want re-running**, and
> AC-NFR-02 wants re-measuring against the signed artefacts rather than the 4.87 MB figure
> from before the rename.

**Devices used so far:**

| Device | OS | Notes |
|---|---|---|
| Lenovo TB305XU | Android 15 (API 35), arm64, 3.7 GB RAM | Neither DoD row. Low-RAM mid-range, so a fair cold-start target |
| `quiblo_api30` emulator | **Android 11 (API 30)**, Pixel 5, x86_64 | The minSdk row, but an emulator — does not satisfy the DoD's "physical device" |
| Pixel Tablet emulator | Android 15 | Desktop-fast; treat its numbers as upper bounds only |

**Android 14 has not been tested at all, and Android 11 only on an emulator.** Both DoD
rows are still open.

**Since `FREEZE.md` Amendment 1 (2026-08-03) the television is part of v1.0**, so this sweep
has a third target: the Haier MatrixTV EE (Google TV, Android 14) recorded in `PLAN-TV.md`
§0. The TV app now exists — T0–T4 are built, and Amendment 4 added its settings and detail
screens, and search arrived on 2026-08-09 — but **none of AC-TV-01…15 have been run as a
sweep**, which is a different thing
from having watched a feature work while writing it. They are listed in §7. v1.0.0 cannot be
tagged on a green phone sweep alone.

---

## 1. Verified mechanically

These are enforced by the build and re-checked on every CI run. They do not need repeating
by hand on the devices.

| ID | Result | Evidence |
|---|---|---|
| AC-NFR-02 | **Pass** — 5.47 MB phone, 4.79 MB TV, against a 25 MB budget | `:app:assembleRelease` and `:app-tv:assembleRelease` at `ebfa928`, unsigned. The release workflow builds and budget-checks both. The TV APK being *smaller* despite depending on every `:feature:*` module confirms R8 strips the phone UI it never references |
| AC-NFR-05 | **Pass** | `./gradlew build detektAll` green at `ebfa928`, 2026-08-04, with `:app-tv` in the build |
| AC-NFR-06 | **Pass** | `enforceNoCompose()` in `quiblo.jvm.library`; no `:core:*`/`:source:*` build file references Compose or a feature module |
| AC-NFR-07 | **Pass** — `:source:m3u` 98.0%, `:source:xtream` 83.9% | `./gradlew coverageAll`, threshold 80 |
| AC-LEGAL-01 | **Pass** | `LICENSE` is the unmodified GPLv3 text |
| AC-LEGAL-02 | **Pass** — 117/117 tracked `.kt` files, `:app-tv` included | Now a CI step over `git ls-files '*.kt'`, rather than the by-hand check it was when this row first claimed to be mechanical |
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
| Add a source, or the manual refresh button | auth + up to 6 catalogue calls, stopping at the first refusal |
| Anything else on a browse screen | none — favouriting, scrolling and filtering are local |
| A live row **the list settles on**, in list mode | one `get_short_epg`, once ever per channel, skipped when cached |
| A live row merely scrolled past | **none** |
| Opening a series | one `get_series_info`, cached for the session since `ebfa928` — re-opening the same series costs nothing |
| Opening a movie | one `get_vod_info`, cached for the session since `ebfa928` |

Nothing refreshes automatically: not on launch, not on tab switch, not on scroll.

**Every one of those passes a token bucket** in `PanelRateLimiter`: a burst of 8, refilling
at one request per 400 ms. A refresh is therefore never slowed, and no amount of scrolling
can exceed two and a half requests a second however many rows go by.

The phone's guide prefetch was per row *entering composition* until 2026-08-04, which is
not the same thing as per row read: a fling composes hundreds of rows in a couple of
seconds. The concurrency cap of three that was supposed to contain it does not — three in
flight at 100 ms each is thirty requests a second. The television app had a focus-settle
delay for exactly this reason and the phone did not. That is the most likely cause of the
provider block seen on 2026-08-03 and again on 2026-08-04.

`XtreamSource` stops asking for fifteen minutes after a panel refuses it, across all four
call paths. Before `a5de4ee` that backoff existed only around the guide, so a blocked
account kept being asked by the catalogue, series and film paths — which is how a short
block becomes a lasting one.

~~**The obvious remaining reduction is caching series and film details for the session.**~~
**Done at `ebfa928`**, in memory rather than in the database, and failures are not cached
at all so a panel coming back out of a block recovers within the session. There is now no
uncached per-open call to the user's panel.

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

---

## 7. Television — not yet swept

The third DoD target: the Haier MatrixTV EE, Google TV, Android 14 (API 34), `armeabi-v7a`,
1.84 GB RAM, rendering at 1920x1080 @320dpi. `PLAN-TV.md` §0 has the full measurement.

Run the whole of this section **with the remote as the only input device.** Unplug or
unpair any mouse first — a mouse silently satisfies criteria a D-pad would fail, which is
the exact defect AC-TV-01 exists to catch.

Install with `adb install -r app-tv/build/outputs/apk/release/…apk` over a pairing brought
up from the TV's Wireless debugging screen (`adb pair <ip>:<port> <code>`; pairing alone is
enough, and a subsequent `adb connect <ip>:5555` is refused).

| ID | State | What has to be shown |
|---|---|---|
| AC-TV-01 | Not run | Every control on every screen — top bar, rows, detail screens, player, sources, settings — reached and operated with the D-pad |
| AC-TV-02 | Not run | No screen and no transition leaves nothing focused, including after back, after a list empties, and after a source is deleted |
| AC-TV-03 | Not run | Back from each screen lands on the category bar; back from the bar exits |
| AC-TV-04 | Not run | Installs, shows a banner, and appears in the Google TV launcher's app row |
| AC-TV-05 | Not run | A held D-pad traversal of the 20k list issues guide requests only for rows focus settled on. Count them at the source, not by eye |
| AC-TV-06 | Not run | D-pad down shows controls, back hides them, playback never pauses for either |
| AC-TV-07 | Not run | A source restored from a backup file with no typing beyond a password |
| AC-TV-08 | Not run | Cold start to interactive under 3s, measured the same way as AC-NFR-01 — six runs after `force-stop`, median reported |
| AC-TV-09 | Not run on hardware | Every phone setting present and effective. Rendering and a DataStore read-back were confirmed on the emulator; the criterion asks for the television |
| AC-TV-10 | Not run on hardware | Confirmed on the emulator — two values landed in two fields with the keyboard up, and the action key showed next-field. The Haier's IME is not the emulator's, so this is exactly the criterion most likely to behave differently |
| AC-TV-11 | Not run on hardware | Film detail with Resume separate from Start from the beginning. Rendering confirmed on the emulator; **resume has never been exercised with a real stored position** |
| AC-TV-12 | Not run on hardware | Series detail with seasons, episodes and the same information a film shows |
| AC-TV-13 | Not run on hardware | Category filtering in Live. Confirmed on the emulator against 11,923 channels; count the requests at the source, not by eye |
| AC-TV-14 | Not run | Search from the bar's first tab: a title found whichever kind it was filed under, results opened with the D-pad, and the query count watched while a term is typed. Do it against the 67k account, not a small one — the cap is the point |
| AC-TV-15 | Not run | Playlists reached from Settings and left again by back, with no Sources tab anywhere in the bar |

**What has been seen on an emulator, and what that is worth.** The television emulator
(`Television_4K`, overridden to 1920x1080 @320dpi — the target device's geometry) was driven
by D-pad for every screen above during development, against the user's real 11,923-channel
Xtream account. That is enough to know the screens compose, focus moves, and settings reach
the store. It is **not** the sweep: the emulator is x86_64 with desktop memory, where the
Haier is `armeabi-v7a` with 1.84 GB, and it has a different IME. No performance figure from
it transfers, and neither does AC-TV-10.

**One item in `agile/001` remains undiagnosed.** The "slow and glitchy" half of #009 has no
diagnosis at all and wants an hour with the television. #008 (a wobble while scrolling) is
closed: the focusable sat inside the animating focus scale, so a focused poster reported bounds
that grew every frame and the vertical list chased them. It is pinned by
`TvBrowseScrollStabilityTest`, which measures the catalogue's position frame by frame, and it
was confirmed on the Haier on 2026-08-06 — every band that should hold still measured 0dp of
movement on every frame while the row scrolled underneath it.

**Continue watching has never been seen populated.** Nothing has been watched on the
emulator, so the row is correctly empty and its filled state is untested.

The phone criteria apply to the TV build too, and none have been run on it. The ones most
likely to behave differently, in order: **AC-PL-05** (the 20k list on 1.84 GB of RAM and a
32-bit ABI — carry over no figure from §2), **AC-PLAY-01…10** (a different player surface
and a different input device), **AC-DATA-01/02** (SAF on a television), and **AC-NFR-08**
(the TV screens have their own strings).

Two criteria do not transfer and should be recorded as not applicable rather than failed:
**AC-PLAY-07** (rotation — the activity is locked to landscape) and **AC-PLAY-10** as
written, since it specifies a tap; AC-TV-06 is its television form.

The AC-NFR-04 finding in §6 applies unchanged to `:app-tv` — same transitive permissions,
same resolution — and its merged manifest additionally declares the two `uses-feature`
entries that make it a TV app. Those are features, not permissions, and are outside what
the criterion covers.
