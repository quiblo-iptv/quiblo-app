<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Execution Plan — Quiblo for Android TV / Google TV

**Status:** T0–T4 and T6 built and merged. T5 (the release sweep) is outstanding, and is the
only thing between here and v1.0.0. Approved for v1.0 by `FREEZE.md` Amendment 1; extended
by Amendment 4.
**Date:** 2026-08-03, last revised 2026-08-04.

## 0. The target device, measured

Not assumed — read over adb from the actual TV on 2026-08-03.

| | |
|---|---|
| Device | Haier MatrixTV EE (`HR9676EU`), Google TV |
| OS | **Android 14, API 34** |
| Panel | 3840x2160, UI rendered at 1920x1080 @ 320dpi |
| CPU | **`armeabi-v7a` — 32-bit** |
| RAM | **1.84 GB total** |
| Features | `android.software.leanback`, `leanback_only` — no touchscreen at all |

**This settles the minSdk question: API 34 is well clear of minSdk 30, so this is a
straightforward port and not a compatibility project.** The wider-reach concern in §7
stands for *other people's* devices but does not affect this one.

Three findings from installing the existing phone APK on it, which it accepts because the
manifest implies only `faketouch` and never requires `touchscreen`:

1. **The whole stack already runs.** Cold start 967 ms, dark theme, correct layout at
   1080p. Every `:core:*` and `:source:*` module works on 32-bit ARM under Android 14.
2. **D-pad focus traversal already works** with the phone's Material 3 components — the
   settings icon takes focus at launch, and the D-pad reaches the navigation bar and moves
   along it. T0 is therefore about making focus *legible and deliberate*, not about making
   it exist. That is a smaller job than this plan assumed.
3. **1.84 GB of RAM, 32-bit.** Every phone performance figure must be re-measured rather
   than carried over. This is the weakest device the project has ever run on.

The APK it accepts does **not** appear in the TV launcher, because it declares no
`LEANBACK_LAUNCHER` category — which is T0's first task, not a bug.

> **This is now in v1.0.** [`FREEZE.md`](FREEZE.md) Amendment 1 (2026-08-03) withdrew the
> "Not a TV app in v1" non-goal for Android TV and Google TV. The consequence to keep in
> view: **v1.0 no longer ships when the phone app is ready — it ships when both are**, and
> the acceptance sweep grows a television.

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

Modelled directly on the Google TV home screen.

```
┌────────────────────────────────────────────────────────────────────┐
│  ⌕ Search   Live   Movies   Series   Favourites          ⚙   2:45  │
│             ─────                                                  │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│   Category name                                                    │
│   ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐            │
│   │      │ │      │ │      │ │      │ │      │ │      │  →         │
│   └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘            │
│                                                                    │
│   Another category                                                 │
│   ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐            │
│   │      │ │      │ │      │ │      │ │      │ │      │  →         │
│   └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘            │
└────────────────────────────────────────────────────────────────────┘
```

**The tabs are text, not blocks.** Plain labels in a row, the active one marked by a thin
underline beneath it and nothing else — no filled pill, no card, no surface behind them.
The focused tab brightens; the selected tab is underlined. Those are two different states
and both have to be visible at once, because on a TV the thing you are pointing at and the
thing you are looking at are frequently not the same.

Also in the bar, matching the reference: **Search on the far left**, and the settings gear
and the clock on the far right.

**Navigation:** up from the first content row focuses the bar, left/right moves along the
tabs, down returns to the content. The bar scrolls away as content scrolls up and returns
on the first scroll down.

### 3.2 Content — scrollable category rows

Below the bar, **one horizontally scrolling row per category**, each under a small heading,
exactly as the reference stacks "Favourite Apps", "Play Next" and so on. Vertical scrolling
moves between categories; horizontal scrolling moves within one.

This replaces the phone app's category *filter*. On a phone you pick one category and see a
grid; on a TV every category is on screen at once and the remote walks through them. The
category picker sheet does not port.

Row contents by tab:

- **Movies and Series** — 2:3 posters, artwork edge to edge, title beneath. Reuses the
  phone's decision that artwork is the interface, with focus replacing hover: the focused
  poster scales up (~1.1) and takes a border, and long titles scroll while focused via
  `basicMarquee` with `WhileFocused`.
- **Favourites** — the same posters, one row per kind rather than per category.
- **Live** — not posters. See §3.3.

Rows are lazy in both directions. A 20,000-item account is many rows of many items, and on
1.84 GB of RAM (§0) nothing may be composed that is not on screen.

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
| Skip buttons | On-screen buttons, **and** left/right seek directly with nothing on screen |
| Drag left half for brightness | **Dropped.** Not app-controllable on a TV |
| Drag right half for volume | **Dropped.** The remote and the TV own volume |
| Screen lock | **Dropped.** Nothing to lock — no touchscreen to pocket |
| Aspect ratio cycling | Kept, on a focusable control |
| Keep screen awake | Kept, and matters more: nobody touches a TV for two hours |

Channel zapping — up/down to change channel with an overlay showing what you moved to — is
the one control TV needs that the phone app has no equivalent of. It is also
`ZapBarOverlay`, deleted in `c0bf585` for never having been wired up. Rebuild it properly
here rather than reverting that deletion.

#### 3.4.1 Two ways in, and the rule that keeps them apart

**Written 2026-08-13, replacing a control layer that had no focusable control in it at all.**

The original design above was right about the five things a remote has keys for and had no
answer for anything else. Everything past those five ended up smuggled onto a key that already
meant something: subtitles arrived on a *second* press of Down, and picture fit on Up, on a
film, where zapping happened not to be using it. **A key map with no keys left cannot take
another feature**, and next and previous episode are two more.

So the player draws its controls where the phone draws them — play in the middle of the screen,
the two skips either side, the two episode steps outside those, and subtitles, audio and picture
fit in a row underneath. The keys still work with nothing on screen, because that is the fastest
way to pause something and there was never a reason to take it away.

**The rule that keeps the two from fighting is one line in `handleKey`: while the controls are
on screen, the arrows belong to focus and the key map answers none of them.** A key consumed
there is a key Compose's focus traversal never sees, so the alternative is every button drawn,
correct and unreachable — the hollow-feature shape arrived at from the opposite direction, and
one this project has already met for real in the licence list. The keys that are *not* arrows —
play, rewind, the channel keys — answer in both states, because a remote's play key means one
thing wherever the viewer is looking.

**Which way each arrow goes is stated, not inferred.** Compose's focus search picks the nearest
rectangle, and with a centred transport row above an options row against the left margin, the
nearest thing below the play button is the options row's *last* button. Both rows carry their
own four directions per button, with `FocusRequester.Cancel` at the ends. Measured, not argued —
`TvPlayerControlsReachableTest` walks it.

#### 3.4.2 Episodes

A series travels with the request, the way a channel list already travelled with a live one.
The player is what asks for the next episode and is the one screen that cannot know what it is:
episodes are fetched per series and are never rows in the channel table.

Two decisions worth not re-deriving:

- **The run is in broadcast order, whatever order the list was drawn in.** The series screen can
  show newest first, and under that arrangement the row below the one playing is the episode
  *before* it. The order things are watched in is not a display preference.
- **It does not wrap, and `Live.zappedBy` does.** A channel list is a ring a viewer walks round;
  a series is a thing that finishes. Rolling off the finale into the pilot would restart a
  series somebody has just finished, from an unattended countdown, with nobody in the room.

The countdown at the end of an episode is `AutoNextDelay` in `:core:model`, offered on the
television's settings screen only — the phone player has no auto-advance yet, and a setting that
changes nothing on the app showing it is the shape this project has deleted nine of.

### 3.5 Sources and Settings

Straight ports of the existing screens with focusable rows. **Sources is reached from
Settings, not from the tab bar** — as of 2026-08-09, and as §3.1's bar always implied: the
reference has five destinations and none of them is a playlist form. Adding a source is done
once; the tab bar is for what is done every evening.

Two constraints:

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

### T6 — Parity (added by FREEZE Amendment 4, 2026-08-04)

Not in the original plan. Added because `agile/001` showed the television was missing most
of the product it was supposed to be a frontend for.

- A settings screen with every phone setting except theme mode and dynamic colour, which are
  meaningless on an always-dark device with no wallpaper.
- Film and series detail screens, sharing their presentation so the two cannot drift apart.
- Category selection in the Live list.
- A continue-watching row.
- **Exit:** AC-TV-09…13 pass on the television. Built and merged; the sweep is outstanding.

Three focus defects were found and fixed along the way, and they are the reason this
milestone existed at all rather than being a matter of writing screens:

- The settings gear was `focusable` with **no `onClick`**, and could not be reached anyway —
  it sits inside the bar's own focusable, so a focus search walks past it into the content.
  The gear is now a position along the bar, not a focusable.
- `TvSourcesScreen` claimed focus on its *first* composition, so selecting the Sources tab
  pulled the remote out of the tab bar entirely.
- The IME ate the D-pad, so field-to-field movement never ran and every field typed went
  into the same box. Movement now goes through the keyboard's own next-field action.

### T7 — Search (2026-08-09)

The magnifier §3.1 has drawn since the first version of this document, finally behind
something. One screen searching live, films and series at once — a viewer looking for a title
does not know which of the three their panel filed it under, and panels routinely file the
same film as both a film and a one-episode series.

- A term is debounced and asked once, capped per kind, and the newest term cancels the one
  before it: a remote's on-screen keyboard delivers characters in bursts, and every burst
  would otherwise queue a query nobody is waiting for.
- A genre filter built from metadata already cached for titles the viewer has browsed past,
  so it costs no requests and works with the key switched off, minus the genres. It says on
  screen how much of the catalogue it can describe, because a filter that silently omits nine
  films in ten is worse than one that admits it.
- Sources leaves the tab bar for Settings (§3.5), which is what makes room for it.
- **Exit:** AC-TV-14 and AC-TV-15 pass on the television.

### T8 — Profiles (2026-08-09)

Who is watching, asked before the catalogue is drawn. Favourites and continue watching belong
to a profile; everything else on the television stays shared. Guest keeps nothing, and keeps
that promise through a power cut rather than only through a polite exit.

- **Exit:** AC-PROF-01…06 pass on the television, AC-PROF-05 — the upgrade — first.

### T5 — Release (week 6)
- Second release track, TV banner and store assets, size budget.
- Full AC sweep on the television alongside the phone sweep.
- **Exit:** **v1.0.0 tagged with both APKs.** Since Amendment 1 there is no phone-only
  v1.0 to ship first — `docs/ACCEPTANCE-SWEEP.md` gains a TV column and the release waits
  for both.

## 6. Acceptance criteria

~~To add.~~ **Added.** AC-TV-01…13 now live in [`ACCEPTANCE.md`](ACCEPTANCE.md) under
`AC-TV — Television`, and are tracked in the sweep like every other criterion. They are not
repeated here: two copies of an acceptance criterion is one copy that goes stale.
09…13 arrived with Amendment 4 and cover the settings and detail screens.

## 7. Risks

| Risk | Mitigation |
|---|---|
| ~~minSdk 30 excludes the target device~~ | **Resolved.** The target runs API 34 (§0). The reach concern remains for other users' older boxes — a README note, not a blocker |
| **32-bit, 1.84 GB RAM.** The weakest device this project has run on | Re-measure everything; carry over no phone figure. The 20k-list and cold-start work already done is the starting point, not the answer |
| Focus bugs are the defining TV defect and do not show up in unit tests | T0 exists to prove the focus model before anything is built on it. Test with the D-pad only — unplug the mouse |
| Guide prefetch under fast scroll gets the account blocked | §3.3. Debounce on focus settling; the `XtreamSource` backoff already covers the failure case |
| Low-end TV boxes have far less RAM than a phone | The 20k-list work already done applies. Re-measure; do not assume the phone figures carry |
| Two apps drift | Shared ViewModels, and no forking. A behaviour change belongs in `:core:*` or the shared ViewModel, never in one UI |
| Scope creep back into v1 | This document is Phase 2. It does not begin until the freeze is amended or lifted |

## 8. Explicitly not in the TV app

Stated so nobody implements them by reflex: brightness and volume gestures, screen lock,
touch-specific affordances, and the phone's long-press interactions. Their TV equivalents
are either the remote's own hardware keys or nothing at all.

**Theme mode and dynamic colour** join the list as of Amendment 4. `QuibloTvTheme` is always
dark on purpose — a television is watched at a distance in a dim room — and a television has
no wallpaper for a dynamic palette. Both controls would change nothing on screen, and a
switch that lies is worse than an absent one.

## 9. First three tasks

1. ~~Find out what the target devices run.~~ **Done — §0. API 34, so this is a port.**
2. Amend `FREEZE.md` with a dated scope entry, or defer until v1.0 ships. **Still open, and
   the only thing blocking T0.**
3. Build T0 and nothing else. Given §0.2, the focus model is likely closer than assumed —
   which makes it cheaper to prove, not safer to skip.
