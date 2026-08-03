<!--
  Vibrato — a free, open source IPTV player.
  Copyright (C) 2026 The Vibrato Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Execution Plan — Vibrato for Android TV / Google TV

**Status:** proposed, not started.
**Date:** 2026-08-03.

> **This is outside the v1.0 freeze.** [`FREEZE.md`](FREEZE.md) §2 lists Android TV and
> Google TV as explicit non-goals — "**Not a TV app in v1**" — and §6 requires scope changes
> to be a dated amendment rather than a quiet edit. Nothing here should be built until that
> amendment exists, or until v1.0 ships and the freeze lifts. The plan is written now so the
> decision can be taken with the cost in front of you.

---

## 1. What this is, in one paragraph

A second Android application, `:app-tv`, sharing every non-UI module with the phone app and
replacing only the presentation layer. Same sources, same database, same player engine, same
settings. A Google-TV-shaped home screen: a category bar across the top that the remote
reaches by pressing up, poster rows for Movies and Series, and a conventional channel list
for Live.

## 2. What is reused, and what has to be written

This is the finding that makes the project small. **Every `:core:*` and `:source:*` module
is already free of Compose** — a rule the phone app enforces at build time (AC-NFR-06), and
which exists precisely so this frontend would be cheap:

| Layer | Modules | TV cost |
|---|---|---|
| Model, database, DataStore, network, media, data | `:core:*` | **Zero.** Used unchanged |
| M3U and Xtream | `:source:*` | **Zero.** Used unchanged |
| ViewModels | inside `:feature:*` | **Zero.** `BrowseViewModel`, `PlayerViewModel`, `SeriesDetailViewModel`, `MovieDetailViewModel`, `SourcesViewModel` and `SettingsViewModel` hold no Compose types and are reused as they are |
| Screens | inside `:feature:*` | **All of it.** Every composable is touch-shaped and none of it ports |

`:app-tv` therefore depends on the existing `:feature:*` modules for their ViewModels and
Koin wiring, and supplies its own composables. The unused phone screens come along as
compiled code but are unreferenced from the TV graph, so R8 strips them from the TV APK —
worth *verifying* on the first release build rather than assuming, since a stray reference
would silently carry the whole phone UI.

**Do not fork the ViewModels.** The moment there are two `BrowseViewModel`s, a fix to the
guide-request guards has to be made twice, and the second one will be forgotten — which is
how the panel-block problem in `xtream-provider-block-469` comes back.

## 3. Design

### 3.1 Home — the Google TV shape

```
┌──────────────────────────────────────────────────────────────┐
│  ⌕   Live    Movies    Series    Favourites    Sources    ⚙  │  ← top bar, focusable
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   [ content for the selected category ]                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

- A `TabRow` from the androidx.tv Compose libraries, pinned to the top.
- **Up from the content focuses the bar; left/right moves between categories; down returns
  to the content.** That is the whole navigation model and it must work before anything
  else is built, because every screen sits underneath it.
- The bar hides on scroll-down and returns on scroll-up, as Google TV's does.
- Settings is the rightmost item rather than a separate destination, so the bar is the only
  top-level navigation.

### 3.2 Movies and Series — poster rows

Reuses the phone app's decision that artwork is the interface: 2:3 posters, title over a
gradient, no separate label row. Differences forced by the ten-foot context:

- **Focus, not hover.** The focused poster scales up (~1.1) and takes a border; everything
  else dims. This is the only cue a viewer has about where they are.
- **Rows by category** rather than one adaptive grid, because a remote traverses a row far
  more comfortably than it traverses a wrapped grid. The category chips become the row
  headings.
- A long title scrolls **while focused** — `basicMarquee` with `WhileFocused`, which is what
  that API is for. The phone's long-press trigger has no equivalent here.
- Detail screen keeps the landscape layout the phone app already has: poster left, title,
  metadata, Play / Resume / Start from beginning, and overview right. It is already the
  correct shape for a TV.

### 3.3 Live — a channel list, not posters

A channel logo is a small wide badge, and a poster grid of them is unreadable. Live gets the
conventional TV list:

```
│ 101  ▸ [logo]  Channel One        Now: Programme name        ▓▓▓▓▓░░░░  20:00–21:00 │
│ 102    [logo]  Channel Two        Now: Something else        ▓▓░░░░░░░  19:30–21:00 │
```

Per row: channel number, logo, name, now-playing title, a progress bar for how far through
it is, and the time span. Next-up on the focused row only.

**The guide data is the risk here, not the layout.** The phone app fetches `get_short_epg`
per channel as rows scroll into view, guarded three ways. A TV list scrolls fast under a
held-down D-pad, so the same code will queue far more requests per second than a finger ever
did. Before this ships, the prefetch must be **debounced on focus settling** — fetch for the
row the user has rested on, not for every row that flew past. Getting this wrong gets the
account blocked, which is not a theoretical risk on this project.

### 3.4 Player

The engine and every setting carry over unchanged. The control layer does not:

| Phone | TV |
|---|---|
| Tap to toggle controls | **D-pad down** shows controls, **back** hides |
| Skip buttons | **Left/right** seeks by the configured interval; centre plays/pauses |
| Drag left half for brightness | **Dropped.** Not app-controllable on a TV |
| Drag right half for volume | **Dropped.** The remote and the TV own volume |
| Screen lock | **Dropped.** Nothing to lock — no touchscreen to pocket |
| Aspect ratio cycling | Kept, on a focusable control |
| Keep screen awake | Kept, and matters more: nobody touches a TV for two hours |

Channel zapping — up/down to change channel with an overlay showing what you moved to — is
the one control TV needs that the phone app has no equivalent of. It is also
`ZapBarOverlay`, deleted in `c0bf585` for never having been wired up. Rebuild it properly
here rather than reverting that deletion.

### 3.5 Sources and Settings

Straight ports of the existing screens with focusable rows. Two constraints:

- **Text entry is miserable on a remote.** Adding an Xtream account means typing a URL, a
  username and a password on an on-screen keyboard. Offer the existing **import** flow as
  the primary path — configure on the phone, export, import on the TV — and treat manual
  entry as the fallback. This also avoids the credential being typed in front of a room.
- The `SettingsViewModel` and its DataStore are shared, but **the TV has its own database
  and its own DataStore file**. Two devices are two installs; nothing syncs. Say so on the
  screen rather than letting someone assume otherwise.

## 4. Module structure

```
:app-tv                    — TV application, manifest, navigation, Koin aggregation
:feature-tv:home           — the top bar and category host
:feature-tv:browse         — poster rows and the focus model, shared by Movies/Series/Favourites
:feature-tv:live           — channel list with now/next
:feature-tv:player         — D-pad playback UI and the zap overlay
:feature-tv:setup          — sources and settings
```

Depending on `:core:*`, `:source:*` and the existing `:feature:*` modules for ViewModels.
The `:core:*` → no-Compose rule stays enforced; the TV modules sit on the same side of that
boundary as the phone features do.

## 5. Milestones

Each exits on something demonstrable on a real device, not on code existing.

### T0 — Shell and focus (week 1)
- `:app-tv` module, leanback manifest, banner, TV launcher intent filter.
- The top bar with all categories, empty screens beneath.
- **Exit:** on a real TV, the remote moves between every category and into and out of the
  content area, with a visible focus indicator at all times. Nothing is reachable only by
  touch.

### T1 — Live (week 2)
- Channel list with logo, number, name, now/next and progress.
- Focus-settled guide prefetch, replacing the scroll-position trigger.
- **Exit:** a 20,000-channel list scrolls under a held D-pad without stutter, and a full
  traversal issues no more guide requests than the number of rows actually rested on.

### T2 — Playback (week 3)
- D-pad player, channel zapping, aspect ratio, keep-awake.
- **Exit:** AC-PLAY-01…10 pass with a remote as the only input device.

### T3 — Movies and Series (week 4)
- Poster rows, focus scaling, detail screens, resume.
- **Exit:** AC-FAV-\*, resume and Start-from-beginning behave as on the phone.

### T4 — Sources, settings, import (week 5)
- Setup screens, import-first onboarding.
- **Exit:** a source can be configured on the TV from an exported backup with no typing
  beyond a password.

### T5 — Release (week 6)
- Separate release track, TV-specific store assets, size budget.
- Full AC sweep on **two physical TV devices**, one Google TV and one Android TV.
- **Exit:** v1.1.0 tagged with both APKs.

## 6. Acceptance criteria to add

Written in the style of [`ACCEPTANCE.md`](ACCEPTANCE.md), which the sweep would extend:

| ID | Criterion |
|---|---|
| AC-TV-01 | Every interactive element is reachable and operable with a D-pad alone. No control requires touch. |
| AC-TV-02 | Focus is visible at all times. At no point is there no focused element on screen. |
| AC-TV-03 | Back from any screen returns to the category bar; back from the bar exits the app. Back never strands the user. |
| AC-TV-04 | The app is installable from the TV launcher, carries a banner, and appears in the launcher's app row. |
| AC-TV-05 | A held D-pad scroll through 20,000 live channels issues guide requests only for rows focus settles on. |
| AC-TV-06 | Playback controls appear on D-pad down and hide on back, without pausing. |
| AC-TV-07 | Text entry is never required to restore a configuration that exists as a backup file. |
| AC-TV-08 | Cold start to interactive under 3s on a low-end Android TV box — a looser bound than AC-NFR-01, because the hardware is weaker. |

## 7. Risks

| Risk | Mitigation |
|---|---|
| **minSdk 30 excludes much of the installed TV base.** Chromecast with Google TV (2020) and many boxes ship Android 9–10. A TV app at API 30 cannot install on them | Decide deliberately: either lower `minSdk` for `:app-tv` only, or accept the reduced reach and say so in the README. **Check the actual API level of the target devices before writing code** — this changes the plan's shape, not just a number |
| Focus bugs are the defining TV defect and do not show up in unit tests | T0 exists to prove the focus model before anything is built on it. Test with the D-pad only — unplug the mouse |
| Guide prefetch under fast scroll gets the account blocked | §3.3. Debounce on focus settling; the `XtreamSource` backoff already covers the failure case |
| Low-end TV boxes have far less RAM than a phone | The 20k-list work already done applies. Re-measure; do not assume the phone figures carry |
| Two apps drift | Shared ViewModels, and no forking. A behaviour change belongs in `:core:*` or the shared ViewModel, never in one UI |
| Scope creep back into v1 | This document is Phase 2. It does not begin until the freeze is amended or lifted |

## 8. Explicitly not in the TV app

Stated so nobody implements them by reflex: brightness and volume gestures, screen lock,
touch-specific affordances, and the phone's long-press interactions. Their TV equivalents
are either the remote's own hardware keys or nothing at all.

## 9. First three tasks

1. **Find out what the target devices actually run.** The minSdk question in §7 decides
   whether this is a straightforward port or a compatibility project.
2. Amend `FREEZE.md` with a dated scope entry, or defer until v1.0 ships.
3. Build T0 and nothing else. If the focus model is not right, none of the rest is worth
   writing.
