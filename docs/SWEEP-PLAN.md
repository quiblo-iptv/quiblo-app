<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Sweep plan

**For whoever is running the acceptance sweep.** You do not need to have seen this codebase
before. Everything you need is here or linked from here.

A **sweep** is the run of every acceptance criterion against real devices, in order, with the
result of each written down. It is the last gate before `1.0.0`, and it is expensive — three
devices, a remote as the only input on one of them, and a working provider account — so it is
worth doing once, properly, rather than twice.

- **The criteria themselves:** [`ACCEPTANCE.md`](ACCEPTANCE.md) — every ID, in full.
- **Where results go:** [`ACCEPTANCE-SWEEP.md`](ACCEPTANCE-SWEEP.md) §5, §6, §7.
- **What is blocked and on whom:** [`STOPPERS.md`](STOPPERS.md).
- **Code merged but never watched behave:** [`TESTING-REQUIRED.md`](TESTING-REQUIRED.md) — session 7 below is that page as a session.

## The one rule

**Every criterion is binary. It passes or it does not. "Mostly works" is a fail.**

Three outcomes are allowed, and only three:

| Result | Means | What you write |
| :---- | :---- | :---- |
| **Pass** | You saw the criterion satisfied, on the device, with your own eyes | What you saw — not "works" |
| **Fail** | You saw it not satisfied | What you saw, on which screen, with a photo or video |
| **Blocked** | You could not run it, and why — a missing account, a stream that will not play | The reason, not a guess at the result |

**Never mark something Pass because it looks right in code, in an emulator, or on a phone when
the criterion is about a television.** Half the defects this project has found were things that
passed everywhere except the device.

---

## Can a machine run any of this?

**Asked 2026-08-13, with no tester available.** The honest answer is *some of it, and not the
half that matters most* — but "some" is worth having, because it is the half that keeps coming
back.

### What is already automated, and what it has caught

53 JVM test classes run on every push. The ones that stand in for a sweep row are Robolectric
Compose tests: `TvPlayerControlsReachableTest` walks every player control with a D-pad at the
panel's own geometry; `TvBarKeysTest` walks the top bar in both directions including both ends;
`TvSearchResultsFitTest` measures whether a focused poster fits the panel it grows on;
`TvSearchRestingCentreTest` measures where the resting search block lands. `core/database` runs
its eleven migrations.

**These caught real faults.** The bar's fourth position was proved reachable without a remote.
The poster that went under the bottom edge when focused was found by arithmetic, not by eye.

### The gap, and the tool that would close it

**This project's most repeated failure is a screen that measures correctly and looks wrong.** The
search screen's resting position has now been wrong four times, each for a different reason, and
twice it passed a measurement while failing a look. A geometry assertion catches "the number
changed"; it cannot catch "the composition is ugly", and nothing here renders a screen and
compares it to the last agreed picture.

**A screenshot test would.** Roborazzi runs on Robolectric — same JVM tests, same CI, no device —
and writes a PNG per composable that a diff can reject. The cost is a golden image per screen and
a habit of looking at the diff rather than accepting it. **Recommended, and the single highest
return available without a tester**, because every one of the last four regressions on that
screen would have shown up as a picture in a pull request.

**An instrumented pass is now possible too**, which it was not a week ago: the television
emulator runs a signed release build with hardware rendering and takes D-pad presses over `adb`.
`reactivecircus/android-emulator-runner` does the same on a GitHub runner. That reaches things
Robolectric cannot — the real focus engine, the real IME over a text field, a real launcher tile
— and it is slow and flaky enough that it should carry a small number of high-value journeys and
nothing else.

### What no tool reaches, and why the sweep still has to happen

| Not automatable | Why |
| :---- | :---- |
| Every `AC-PLAY-*` and `AC-XT-*` | They need a provider account and a real stream. There is no fixture for "the provider is slow today" |
| "Reads from three metres" | A 1080p window at arm's length is a different instrument from a 50-inch panel across a room. Both wrong answers this project shipped looked fine on a desk |
| Remote key repeat, HDMI colour, panel scaling | Hardware behaviour, on hardware |
| `AC-NFR-*` timings | A CI runner's clock says nothing about a television box's |

**So the shape is:** automate the geometry and the reachability, add pictures so the look has a
record, and spend the tester on the stream and the room. **The emulator retires "can this be
reached". It does not retire "is this right".**

---

## Before day one

### 1. The three devices

| Row | Device | Why it is in the list |
| :---- | :---- | :---- |
| **A** | A **physical Android 11** phone or tablet | The `minSdk` floor. An emulator does **not** satisfy this row |
| **B** | A **physical Android 14** phone or tablet | Never tested at all, on any build |
| **C** | The **Android TV / Google TV** set | Since `FREEZE.md` Amendment 1 the television is part of `1.0.0`. A green phone sweep is not a `1.0.0` |

### 2. The builds

Take them from **[GitHub Releases](https://github.com/quiblo-iptv/quiblo-app/releases)**, not from
a local build — the criteria are about the artefact people install.

**This plan is written against `v0.5.0`**, the release current on 2026-08-12. If the releases page
shows a newer one, take the newer one and read every `0.5.0` below as that version — a sweep is
worth most against what people can actually download today.

**`v0.5.0` carries work that no device has seen**, merged on 2026-08-12: a `1.0.0` metadata defect
(#024) and four `1.1.0` items. **Session 7** covers them. If you are short of time, session 7's
rows fold into sessions 4 and 6 by device.

| File | Goes on |
| :---- | :---- |
| `quiblo-v0.5.0.apk` | Rows A and B (phone / tablet) |
| `quiblo-tv-v0.5.0.apk` | Row C (television) |

**Check the download before installing it.** Each APK ships a `.sha256` beside it:

```sh
sha256sum -c quiblo-v0.5.0.apk.sha256
```

**Record the version you actually tested, in every result.** "It worked" against an unknown build
is not a result.

### 3. Two install traps that will cost you an hour each

- **A build signed with the debug key can never be upgraded over by a real release.** If a device
  already carries a build somebody handed over before signed releases existed, the install fails
  with a signature conflict. **Uninstall first.**
- **On a television, the blocking install may belong to another user on the device.** A build
  installed under a second profile blocks the install while being invisible in the launcher you
  are looking at. If a set refuses a release for no visible reason, check that first —
  `adb shell pm list packages --user 10 | grep quiblo`.

### 4. The remote is the only input on row C

**Unpair every mouse before you start.** A mouse silently satisfies criteria a D-pad would fail,
which is exactly the defect `AC-TV-01` exists to catch. Installing over `adb` is fine:

```sh
adb pair <ip>:<port> <code>      # from the TV's Wireless debugging screen
adb install -r quiblo-tv-v0.5.0.apk
```

### 5. What must be prepared before the day — check these off first

Three of these are **blocking**, and every one of them has stalled a sweep day before:

| Prerequisite | Why | State |
| :---- | :---- | :---- |
| **A working Xtream account** | `AC-XT-01…06` and `AC-PL-07` cannot run without one. The account used previously is **API-blocked at the provider and returns 469** to everything | **Blocking — see [`STOPPERS.md`](STOPPERS.md) §S2** |
| **A playlist of real, legal streams** | The synthetic playlist points every entry at `.invalid`, so it cannot exercise the player at all. Every `AC-PLAY-*` needs real ones | **Blocking — §S3** |
| **A 20,000-entry M3U** | For `AC-PL-05`, `AC-TV-05`, `AC-NFR-01`. Generate it: `python tools/gen_playlist.py 20000 big.m3u` | Ready — no account needed |
| **A file with two audio tracks** | `AC-PLAY-04` has never been run on either app | Needed |
| **A release-key build from before profiles** | For `AC-PROF-05` — see the box below | **Ready — built and staged 2026-08-11** |
| **A device already carrying a metadata cache** | For #024's upgrade half — session 7.1 | Any device that has run an earlier build with a TMDB key |

> **`AC-PROF-05` has its build now — it did not when this plan was first written.** The criterion
> is *"upgrading from a build without profiles keeps every favourite and resume point"*. Profiles
> landed on **2026-08-09** and `v0.2.1`, the earliest release ever published, already contains
> them — so no published release could start this test, and a debug-signed old build cannot be
> upgraded over by a signed release either.
>
> **Two `0.2.0` APKs were built from `572d849` and signed with the release key on 2026-08-11.**
> They sit outside the repository at `~/Dev/mywrok/quiblo/sweep-artefacts/`, with a checksum
> beside each, and both carry certificate `9f4f77c4…0c74ec` — the same one on `v0.5.0`. That last
> check is the one worth repeating rather than assuming: an APK signed with any other key installs
> perfectly and then refuses the upgrade, on the day, in front of you.
>
> Install `0.2.0`, add favourites, leave something part-watched, **then** install `v0.5.0` over it.
> It is the highest-consequence unrun criterion in the project: a fault there presents as an empty
> catalogue, not a cosmetic bug. See [`STOPPERS.md`](STOPPERS.md) §S9.

---

## The order, and why it is this order

Run the sessions in this sequence. Two of them are ordered by facts about the app rather than by
preference:

1. **Session 1 is a fresh install, and it is first.** The first-launch terms screens only appear
   on an install that has never run. Once you have pressed through them they are gone until the
   app's data is cleared — so if you skip session 1, `AC-LEGAL-06…09` cannot be tested that day
   at all.
2. **`AC-PROF-05` needs its own device state**, prepared before the day (see the box above).

| Session | Device | Roughly | What it covers |
| :---- | :---- | :---- | :---- |
| 1 | C — television | 30 min | First launch: terms, profiles, About |
| 2 | C — television | 2 h | The twelve round-3 defects |
| 3 | C — television | 2 h | `AC-TV-01…15` |
| 4 | A — Android 11 | 2 h | Playlists, playback, favourites, backup |
| 5 | B — Android 14 | 2 h | The same, on the second DoD row |
| 6 | A or B — phone | 1 h | Profiles and the catalogue scan |
| 7 | A or B, then C | 1 h | **New in `v0.5.0`** — never seen on any device |

---

## Session 1 — Television, fresh install

**Device:** C. **Build:** `quiblo-tv-v0.5.0.apk`. **Precondition: the app has never run on this
device.** If it has, uninstall it, or clear its data from Android settings.

| # | Criterion | Screen | What to do | Passes when |
| :---- | :---- | :---- | :---- | :---- |
| 1.1 | `AC-TV-04` | TV launcher | Install, then look in the launcher's app row | Quiblo is there, with a banner, and opens from it |
| 1.2 | `AC-LEGAL-06` | First launch, screen 1 | Open the app. Read it. Press the button with the D-pad | Two screens appear before anything else. Both are readable and both can be got past **with the remote alone** |
| 1.3 | `AC-LEGAL-07` | First launch, screen 2 | Read the second screen without following the link | The terms themselves are on the screen — sources are yours, nothing leaves the device, no warranty. The link is extra, not the only copy |
| 1.4 | `AC-LEGAL-06` | First launch | Try to find a way to decline | There is none, by design. Not a fault |
| 1.5 | `AC-PROF-01` | Who is watching | After the terms | The chooser appears **before** any catalogue. It is centred and its avatars are circular. No favourite or resume point is visible until you choose |
| 1.6 | `AC-LEGAL-08` | — | Choose Guest. Force-stop the app. Reopen it | The terms do **not** appear again. The profile chooser **does** |
| 1.7 | `AC-LEGAL-08` | — | Choose a named profile, force-stop, reopen | Terms still absent. Consent is per install, not per profile |
| 1.8 | `AC-TV-08` | Cold start | `adb shell am force-stop dev.quiblo.tv`, then launch, six times. Record `TotalTime` from `am start -W` | Median under **3000 ms** |
| 1.9 | `AC-LEGAL-03` | Settings → About | Walk to the bottom of Settings. Open Open source licences. **Walk to the last entry with the D-pad** | Every entry can be reached. Not just the first two |
| 1.10 | — | Settings → About | Read the version | It says `0.5.0`, matching what you installed |
| 1.11 | `AC-LEGAL-03` | Settings → Artwork | Look under the channel-logos and metadata controls | Both service sentences are present: the TMDB one and the iptv-org one |

**After session 1, add a source** — the 20k M3U for the sessions that need volume, and the Xtream
account if you have one. Sessions 2 and 3 need a populated catalogue.

## Session 2 — Television, the twelve round-3 defects

**Device:** C. **Build:** `v0.5.0`. These are reported faults that have been fixed in code. **Two
of them were fixed once, rejected on this television, and rebuilt** — those two are marked.

| # | Defect | Criterion | Screen | What to do | Passes when |
| :---- | :---- | :---- | :---- | :---- | :---- |
| 2.1 | **#015** ⚠ *rebuilt after being rejected* | `AC-TV-11/12` | Series detail | Open a **multi-season series** from the catalogue | The screen opens at its **top**: whole poster, title on screen, nothing cut off. It does not scroll on its own |
| 2.2 | **#015** ⚠ | `AC-TV-11` | Film detail | Open a **film**. This is the control — do not skip it | Same: whole poster, title visible, no self-scrolling |
| 2.3 | **#021** ⚠ *unfixed — capture video* | `AC-TV-10` | Settings → key field | Open the API-key field with the remote. **Record video for a full second** | The screen is still. If it shakes, the video is the deliverable — see "Reporting a fault" |
| 2.4 | #020 | `AC-TV-03` | Player → series | Play an episode, press back once, then again | First back reaches **that series'** screen with **that episode focused**. Second back reaches the catalogue |
| 2.5 | #016 | `AC-PROF-01` | First launch | Force-stop, reopen | The chooser appears every time — not only on a fresh install |
| 2.6 | #012 | `AC-TV-02` | Home → continue row | Look at the **first** tile, focused and unfocused | Whole in both states. Not clipped by its container |
| 2.7 | #013 | `AC-PLAY-01` | Player | Play a channel, then switch to another | No frame of the previous stream survives the switch |
| 2.8 | #014 | `AC-TV-11/12` | Detail screen | Look at the favourites button on a long title. Then remove a title from continue-watching | The label fits. A remove control exists and works |
| 2.9 | #017 | `AC-NFR-08` | Favourites | Read the row headings | They read Live / Movies / Series. **Never `VOD` or `SERIES`** |
| 2.10 | #019 | `AC-TV-14` | Search → Advanced | Search a term that matches series | A Series heading is present, titles are not cropped when focused, and nothing is labelled "Films" |
| 2.11 | #018 | — | Search → Advanced | Open Advanced on a large catalogue | The wait for genres is explained on screen, not blank |
| 2.12 | #022 | `AC-TV-09` | Settings | Walk down the right-hand column with the remote | Every control lines up. The key field is not offset from the rest |
| 2.13 | #023 | `AC-PLAY-04` | Player | Play a file with two audio tracks. Open the track control | Both tracks are listed and switching works. **This criterion has never been run** |

## Session 3 — Television, `AC-TV-01…15`

**Device:** C. **Build:** `v0.5.0`. **Remote only.**

| # | Criterion | Screen | What to do | Passes when |
| :---- | :---- | :---- | :---- | :---- |
| 3.1 | `AC-TV-01` | Every screen | Walk the whole app: tab bar, rows, detail, player, sources, settings | Every control is reachable and operable with the D-pad. Nothing needs touch |
| 3.2 | `AC-TV-02` | Every screen | Watch the focus indicator, including after back, after a list empties, and after deleting a source | Something is always focused. Never nothing |
| 3.3 | `AC-TV-03` | Everywhere | Back out of every screen | Back pops exactly one step and never strands you. No screen needs two presses to leave |
| 3.4 | `AC-TV-05` | Live, 20k list | Hold D-pad down through the list. Count guide requests at the source | Requests are issued only for rows focus **settled** on — not one per row passed |
| 3.5 | `AC-TV-06` | Player | Press D-pad down, then back | Controls appear, then hide. **Playback never pauses** for either |
| 3.6 | `AC-TV-07` | Settings → Backup | Restore a configuration from a backup file | No typing needed beyond a password |
| 3.7 | `AC-TV-09` | Settings | Change every setting, then check the app's behaviour — not the label | Each change actually changes what the app does. Theme and dynamic colour must be **absent**, not present-and-inert |
| 3.8 | `AC-TV-10` | Settings → key field | Type into consecutive fields with the keyboard up | Each value lands in its own field, and a field can be left with the keyboard still up |
| 3.9 | `AC-TV-11` | Film detail | Open a film you have partly watched | It shows what the film is before playing, and offers **Resume** separately from **Start from the beginning**. Resume has never been exercised with a real stored position |
| 3.10 | `AC-TV-12` | Series detail | Open a series | Seasons and episodes, plus the same information a film shows. Pressing the series never sends its own URL to the player |
| 3.11 | `AC-TV-13` | Live | Filter by category with the remote. Count requests | Filtering works and issues **no request per category passed over** |
| 3.12 | `AC-TV-14` | Search | Search a title filed under any kind. Watch the query count while typing | Found from one screen whatever it was filed under, every result openable by D-pad, and **no query per keystroke**. Use the large account — the cap is the point |
| 3.13 | `AC-TV-15` | Settings → Sources | Open playlists from Settings, then press back | Back returns to **Settings**, not to the catalogue. There is no Sources tab in the bar |
| 3.14 | `AC-TV-16` | Player | Press down. Walk the transport row left and right, press down again, walk the second row. Let it time out, then press left | Every button takes focus; the second press of down reaches the lower row; after the timeout **left seeks** rather than moving focus. Focus is never nowhere |
| 3.15 | `AC-TV-17` | Player, series | From mid-season, step next and previous. Check the first and last episode. Then watch one to its end | Each step plays the neighbouring episode from the start, and back returns to the series **once**. No previous button on the first episode, no next on the last. The end-of-episode banner counts down, Stop ends it, and it never appears on a film |
| 3.16 | `AC-TV-17` | Series listed newest first | Turn on "Newest first", then step to the next episode | It plays the episode that comes **after** in broadcast order, not the row below on screen |

## Session 4 — Phone, Android 11 (row A)

**Device:** A. **Build:** `quiblo-v0.5.0.apk`. Fresh install, then repeat the marked rows over an
upgrade.

| # | Criterion | Screen | What to do | Passes when |
| :---- | :---- | :---- | :---- | :---- |
| 4.1 | `AC-LEGAL-06/07` | First launch | Same two screens as the television. The link should open a browser here | Readable, passable, and the link works — but the text is on screen either way |
| 4.2 | `AC-PL-02` | Sources | Add a local `.m3u` via the file picker | Parses identically to the remote path |
| 4.3 | `AC-PL-03` | Browse | Watch logos load | Lazy, with a placeholder on failure. A broken logo never blocks a row |
| 4.4 | `AC-PL-06` | Browse | Add a playlist with entries lacking `group-title` | They appear under a single "Ungrouped" |
| 4.5 | `AC-PL-07` | Sources | Add a URL that 404s, one that times out, one serving HTML | Each gives a specific human-readable error. **Never a stack trace or a bare "Error"** |
| 4.6 | `AC-XT-01…06` | Sources | Add the Xtream account. Try wrong credentials, odd URL shapes, an expired account | Categories populate; auth errors are distinguished from network errors; URL forms normalise; credentials are stored encrypted |
| 4.7 | `AC-EPG-01…05` | Live, detail | With an Xtream source: check now/next, times, and an M3U source for comparison | Guide renders, times are in the device's zone, an M3U shows no broken guide UI, cached guide renders offline |
| 4.8 | `AC-PLAY-01…13` | Player | Real streams: HLS, MPEG-TS, an MP4/MKV, a dead URL, a rotation, an incoming call | Each behaves as `ACCEPTANCE.md` states. **`AC-PLAY-04` and `AC-PLAY-11` have never been run** |
| 4.9 | `AC-FAV-01…05` | Favourites | Favourite each kind, kill the app, refresh the source, search within a category | Favourites persist and survive a refresh; search filters within 200 ms on the 20k list |
| 4.10 | `AC-DATA-01…05` | Settings → Backup | Export, import on a fresh install, inspect the file, import a `schema_version: 99` file | Round-trips; **no plaintext password in the file**; the future-version file is refused by name |
| 4.11 | `AC-NFR-01` | Cold start | Six launches after force-stop, populated database | Median under **2000 ms** |
| 4.12 | `AC-NFR-03` | — | Packet capture on a clean install, no key configured | **Zero** requests to any host you did not configure |
| 4.13 | `AC-NFR-08` | Every screen | Read every screen for hardcoded strings; switch the device to Arabic | No untranslated string; the UI mirrors right-to-left |
| 4.14 | `AC-LEGAL-09` | Upgrade | Install `v0.5.0` over an earlier release that has already accepted the terms | The terms do **not** appear again |

## Session 5 — Phone, Android 14 (row B)

**Device:** B. **Build:** `quiblo-v0.5.0.apk`. **Repeat every row of session 4.** This row has
never been tested on any build, so treat nothing as covered by session 4 — that is the whole
point of a second OS row.

## Session 6 — Profiles and the catalogue scan

**Device:** A or B. Needs a metadata key configured for the scan half.

| # | Criterion | Screen | What to do | Passes when |
| :---- | :---- | :---- | :---- | :---- |
| 6.1 | `AC-PROF-05` | Upgrade | **The prepared device from the box above.** Favourites on a pre-profiles build, then upgrade | Every favourite and resume point survives, under a profile named **Default** |
| 6.2 | `AC-PROF-02` | Favourites | Favourite different titles under two profiles | Neither profile can see the other's |
| 6.3 | `AC-PROF-03` | Settings | Switch profile, then check playlists, player settings, hidden categories, metadata key | All unchanged by the switch |
| 6.4 | `AC-PROF-04` | Guest | Favourite something as Guest, leave. Then again, force-stopping mid-session | Gone both times |
| 6.5 | `AC-PROF-06` | Profiles | Delete a profile | Its favourites and resume points go; every other profile's stay |
| 6.6 | `AC-META-01` | Settings | **With no key configured**, packet capture | No contact with the metadata service at all. A scan cannot even be started |
| 6.7 | `AC-META-02` | Settings → scan | Scan a catalogue with duplicate quality variants. Count requests at the source | **One request per distinct title**, not one per row |
| 6.8 | `AC-META-03` | Settings → scan | Watch the sustained rate for the whole run | Within the documented budget throughout. A concurrency cap does not satisfy this |
| 6.9 | `AC-META-04` | Settings → scan | Provoke a refusal — a wrong key, or a rate limit | The scan stops, and **nothing is cached as an answer** for the titles in flight |
| 6.10 | `AC-META-05` | Settings → scan | Cancel mid-scan, restart. Then re-run a completed scan | Resumes where it stopped; a completed scan re-run issues no requests |
| 6.11 | `AC-META-06` | Settings → scan | Leave the screen mid-scan and come back | Progress is visible and survives leaving |

## Session 7 — What `v0.5.0` added, and no device has seen

**Device:** A or B for 7.1 to 7.4, C for 7.5. Merged 2026-08-12.

**One of these is a `1.0.0` defect and the rest are `1.1.0`.** 7.1 is the `1.0.0` one, and it is
the only row here whose failure a viewer is looking at today rather than one that waits for a
screen to be opened.

| # | Item | Screen | What to do | Passes when |
| :---- | :---- | :---- | :---- | :---- |
| 7.1 | **#024** — the metadata cache key | Movies | With a metadata key set, find two films sharing a name and differing in year — *Dune*, *The Thing*, *Suspiria*. Open **both** detail screens | Each shows its **own** plot, poster, rating and cast. If they show the same ones, the defect is unfixed |
| 7.2 | #024, the upgrade half | Browse, Search | Install `v0.5.0` over a build that already held a metadata cache | The first browse is **as slow as a first install** and coverage reads **0%** until you scan again. Both are intended. Report only if they do not recover |
| 7.3 | **INC-F0** — profile avatars | Who is watching | Create a profile on the **phone**, pick a face from the row above the name field. Force-stop, reopen | The face chosen is the face shown. A profile that existed **before** this build shows its **initial on a colour**, not a face nobody picked |
| 7.4 | **INC-F8** — settings scrolls once | Settings | On an account with **hundreds** of categories, drag anywhere on the screen | One drag reaches the last category **and** one drag reaches the cards above them. There is one scrolling surface, not two |
| 7.5 | **INC-F3** — forget one thing | Home → continue row | **Phone only.** Long-press a tile, take *Remove from watch history* | The tile goes and stays gone after a force-stop. A **series** tile must vanish entirely, not be replaced by its previous episode |
| 7.6 | **INC-E4** — corner radius | Both apps, every screen | Look at cards, dialogs, chips, fields, player controls | Rounder, intentional at three metres and at arm's length, nothing clipped by its own radius. **If it is simply not liked, say so** — the whole scale is three numbers |
| 7.7 | #024, the cost | Settings → scan | Run a scan on a large account and note how long it takes | An undated title is now its own cache row, so there are slightly **more** lookups than before. If the scan is noticeably longer, say so with a number |

**7.3's upgrade half is `AC-PROF-05` in miniature** and uses the same prepared device — run them
together.

### Session 7b — the television's furniture, added 2026-08-13

**Television only, and all of it is a look.** Every row has been walked with a D-pad on the
emulator and screenshotted, so none of it is a question about whether the control exists. What is
unanswered is how each reads on a panel, from a sofa. `TESTING-REQUIRED.md` §A7 carries the same
list with the reasoning behind each.

| # | Item | Screen | What to do | Passes when |
| :---- | :---- | :---- | :---- | :---- |
| 7.8 | **The launcher tile** | Android TV home → Apps | Look at the Quiblo tile from where you normally sit | It reads as Quiblo before you read the label under it. No bar down either side |
| 7.9 | **The gear and the face** | Any screen with the top bar | Walk right past the last tab, then back left | The two icons read as **one group** at the end of the bar, not two strays. The highlight on each is obvious from the sofa |
| 7.10 | **The resting search screen** | Search, nothing typed | Look at the mark, the name, the field and Advanced together | The block sits on what **looks** like the middle of the panel, and Advanced reads as belonging to the field above it. **It is deliberately a little above the true middle** — say so if that reads as too much or too little |
| 7.11 | **Picking a face** | Add profile | Type a name, then walk the row of faces | The faces are told apart from the sofa and the chosen one is obviously chosen. Say whether the keyboard burying the row while you type is a problem in practice |

---

## Reporting a fault

A report that can be acted on has five things. A report without them costs a day of guessing:

1. **Which build** — the version string from Settings → About, not "the latest".
2. **Which device** — and which OS version.
3. **What you did**, in presses. "Opened a series from Movies with the D-pad" beats "opened a
   series".
4. **What you saw**, against what the criterion says should happen.
5. **Evidence** — a photo for something static, **video for anything that moves**.

**For anything that shakes, flickers or jitters, video is the deliverable, not a description.**
Hold the camera still, or rest it on something. Get something known to be motionless in frame —
the edge of the screen, a wall — so that a shaky hand can be told apart from a shaking app.
`adb shell screenrecord` is **not** a substitute: it drops frames and can smooth out the very
thing being reported.

## What is already known — do not re-report these

- **`#021`, the settings shake, is unfixed.** It has been diagnosed twice and is still open. Your
  job on it is the video, not a verdict.
- **The metadata scan stopping on a rate limit is deliberate**, not a bug. It keeps what it found
  and resumes from there.
- **A provider that stops answering while streams keep playing is the provider**, not the app.
  The app backs off for fifteen minutes and says so.
- **The phone build will not appear in a television launcher.** By design; install the `-tv` one.
- **The television has no long-press on the continue row.** The phone half shipped; the television
  half is `STOPPERS.md` **S12** — that app contains no dialog anywhere, and the pattern for a
  one-question question on a remote has not been decided. Remove-from-history **is** on the
  television's detail screen (session 2.8).
- **The television search screen is unchanged.** Its logo, its Advanced placement and the
  travelling glow are `STOPPERS.md` **S10**, deliberately not built — each is a question about how
  a screen reads from three metres, and building all three to keep one is the expensive order.
- **There is no avatar picker on the television, and no avatar control beside the gear.**
  `STOPPERS.md` **S11**. A profile made on the phone shows its face on the television already; one
  made on the television gets the initial-on-a-colour, which is a picture rather than a gap.

## Where results go

Write each result into [`ACCEPTANCE-SWEEP.md`](ACCEPTANCE-SWEEP.md) — §5 for the phone rows, §7
for the television — replacing "Not run" with **Pass**, **Fail** or **Blocked**, and the evidence
beside it. **A row that says Pass with no evidence is a row nobody can trust six weeks later**,
and this project has already had to re-run results for exactly that reason.
