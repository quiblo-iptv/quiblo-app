**Bug Fix Plan of Quiblo**

Plan for the ten bugs raised in [`001 Bug Reporting of Quiblo.md`](001%20Bug%20Reporting%20of%20Quiblo.md).

**Created:** 2026-08-04, against commit `681f91c`.

---

## Why this document exists

The ten reported bugs are not one kind of problem. Five are defects in code that exists
(#001, #003, #008, #009, #010). Five are screens the television frontend never got at all
(#002, #004, #005, #006, #007).

`FREEZE.md` Amendment 1 admitted the television into v1.0 on the argument that it is "the
same player with a different frontend, not a different product". That claim does not
currently hold: on a television a viewer cannot open a film, cannot see a series' episodes,
cannot reach any setting, and cannot pick a category in the Live list. This bug sheet is the
evidence. Closing it is what makes Amendment 1 true.

Two bugs are severe on their own terms. **#009** — the player treating films and series as
live channels — is architectural rather than cosmetic. **#010** — a mobile ANR while
scrolling — is a hang on the phone build that the acceptance sweep would have to fail. Both
live in code the two apps share.

## Decisions taken

| Question | Chosen |
|---|---|
| Sequencing | Fix what is broken first, then build what is missing |
| TV settings (#004) | Full parity, including fixing the IME focus trap |
| Scope | `FREEZE.md` **Amendment 4**; v1.0.0 does not tag until this work passes |

## Status

**Phase 1 is merged.** Two items did not survive contact and are recorded honestly below
rather than ticked off.

| Bug | Phase | State |
|---|---|---|
| #001 Loading time in Movies & Series | 1.1 + 1.2 | **Fixed**, pending on-device confirmation |
| #002 Live list has no category | 2.1 | **Fixed** — verified on the emulator |
| #003 Hover touching the category title | 1.4 | **Fixed** |
| #004 No settings screen | 2.2 | **Fixed** — all settings but theme; see below |
| #005 Movies missing info | 2.3 | Not started |
| #006 Movies missing history row | 2.4 | Not started |
| #007 Series missing everything Movies has | 2.3 | Episode list done in 1.5; the rest not started |
| #008 Screen wobble while scrolling | 1.3 | **Open — diagnosis was wrong**, see below |
| #009 The player is broken | 1.5 | **Half fixed** — see below |
| #010 App frozen (mobile) | 1.1 | **Fixed**, pending on-device confirmation |

### #004 — done, and what it dragged out with it

Done in full: playback settings, the channel-logo switch, the metadata key, category
hide/rename, and backup export/import over SAF — which also exercises **AC-TV-07** for the
first time.

The two open add-source bugs from `PLAN-TV.md` went with it, because the settings screen
needed the same fix:

- **The IME ate the D-pad.** `TvField` intercepted up and down itself, which works only
  with the keyboard *hidden*. With it up — the normal state on a television — the IME takes
  the D-pad, so the interception never ran and every field typed landed in the same box.
  Field movement now goes through the keyboard's own `ImeAction.Next`. `TvTextField` is
  shared rather than copied, so this cannot be fixed in one screen and forgotten in another.
- **A rejected save was silent.** The form closed whether or not the save was accepted.
  Both add functions now report acceptance and the form says what is missing.

Two things found while building it, both worth knowing:

- **The gear could never have been reached.** The bar left the right-key unconsumed at the
  last tab expecting focus to land on the icon; the focus search walks past it into the
  content instead, because the icon is inside the bar's own focusable. The gear is now a
  position along the bar rather than a focusable, matching the tabs.
- **`TvSourcesScreen` stole focus on first composition**, so selecting the Sources tab
  pulled the remote out of the bar entirely. Now only a later change claims focus.

**Theme mode and dynamic colour are deliberately not on the television.** It is always dark
by design and has no wallpaper for a dynamic palette, so both would be controls that change
nothing — the hollow-feature shape this project has already deleted once. Say so rather than
ship them; reversing this is a few lines if wanted.

### #008 — the diagnosis in this plan was wrong

§1.3 claimed a Live row grows taller when its guide arrives, relaying out the list. It does
not. `ChannelLogo` is a fixed 64x40 box, so the row is `max(24, 40, 24, …) + 20 = 60dp`
whether `NowPlaying` draws two lines or nothing at all — the logo dominates either way and
the guide cannot move anything. A fix was written against that story and then reverted
rather than shipped.

The remaining static suspects, none confirmed: the `basicMarquee` that `Poster` adds and
removes on focus change, and the absence of `contentPadding` on the Live `LazyColumn`, which
makes a D-pad step scroll the focused row flush to the screen edge. **This one needs
watching on the device before anything else is changed.**

### #009 — the other half is unmeasured

The architectural half is fixed: films play as films, series open an episode list, and the
channel keys no longer zap during a film. "Slow, glitchy, buggy" has no diagnosis yet. Two
suspects to measure on the Haier before changing either: the `graphicsLayer` scale applied
to the `SurfaceView`, and the `BufferMode` default on 1.84 GB and a 32-bit ABI.

---

## Step 0 — land the work already in the tree

The working tree carries roughly 1,073 uncommitted lines across 35 files, plus a new
`:source:iptvorg` module, `WatchHistoryRepository`, `ChannelLogoRepository`,
`ContinueWatchingRow` and database schemas 8 and 9. Commit that before starting. Every phase
below touches the same files, and mixing a feature branch's worth of new work into a bug-fix
diff makes both unreviewable.

This matters for **#006** in particular: continue-watching already exists on the phone in
that uncommitted work, so the television bug is "port it", not "build it".

---

## Phase 1 — fix what is broken

### 1.1 — #010 mobile ANR, and half of #001 — the shared data path

Three separate causes, all in code the phone and the television share. The user's panel
returns **67,567 channels**, which is the number each of these has to survive.

**A. No index serves the browse query.**

`ChannelEntity` (`core/database/src/main/kotlin/dev/quiblo/core/database/entity/Entities.kt:60`)
indexes `sourceId`, `(sourceId, groupTitle)` and `(sourceId, stableKey)`.
`ChannelDao.observeBrowse` (`core/database/.../dao/Daos.kt:99`) filters on
`sourceId AND kind` and sorts `ORDER BY c.sortIndex ASC`. Nothing covers that combination.
SQLite matches every row for the source, filters `kind` row by row, then builds a temporary
B-tree to sort — on every emission. `observeCategoriesByKind` (`Daos.kt:142`) groups over
the same unindexed predicate.

- Add `Index("sourceId", "kind", "sortIndex")` to `ChannelEntity`.
- Bump the database to version 10 and add `MIGRATION_9_10`, following the shape of
  `MIGRATION_7_8` (`core/database/.../Migrations.kt:197`), which already adds an index this
  way.
- Commit the generated `core/database/schemas/…/10.json`.

The `name LIKE '%query%'` predicate cannot use an index and does not need to: the leading
wildcard rules it out, and the query is already debounced (`BrowseViewModel.kt:203`).

**B. 67,000 entities are mapped to domain objects on the main thread.**

`ChannelRepository.observeBrowse` (`core/data/.../ChannelRepository.kt:66`) ends in
`.map { rows -> rows.map { it.channel.toDomain(...) } }` with **no `flowOn`**. A Flow
operator runs in its collector's context. The collector is the `combine` inside
`stateIn(viewModelScope, …)` (`BrowseViewModel.kt:212`, `:196`), and `viewModelScope` is
`Dispatchers.Main.immediate` — so 67,567 objects are allocated on the main thread every time
Room re-emits, which is on every write to the `channels` table.

Add `.flowOn(Dispatchers.Default)` to `observeBrowse` and `observeFavorites`. This is the
single most likely cause of #010 and needs no other change to justify itself.

**C. The guide is combined into feeds that cannot display it.**

`feedFor` (`BrowseViewModel.kt:198`) combines `guideRepository.observeNowPlaying(sourceId)`
into *every* feed, including Movies, Series and Favourites, none of which render a
programme. `observeNowPlaying` (`Daos.kt:291`) selects every programme airing now across the
whole source, unfiltered by channel.

Return `flowOf(emptyMap())` for non-`LIVE` feeds, the way `historyFor`
(`BrowseViewModel.kt:245`) already does for history. Consider an index on
`(sourceId, startEpochMillis, endEpochMillis)` in the same migration.

**Tests.** A `ChannelDaoTest` asserting the browse query's plan uses the new index
(`EXPLAIN QUERY PLAN`: no `SCAN channels`, no `USE TEMP B-TREE`), and a repository test
asserting `observeBrowse` does not emit on the main dispatcher.

### 1.2 — #001 Movies & Series load time, the television side

Two further costs, specific to `TvPosterRows`:

- **Grouping runs during composition.** `app-tv/.../ui/browse/TvPosterRows.kt:103` computes
  `state.items.groupBy { it.groupTitle }` inside a `remember`. On a 30,000-item catalogue
  that is a main-thread `groupBy` allocating one list per category during the first frame.
  Move it into `BrowseViewModel` as a grouped projection computed on `Dispatchers.Default`
  alongside 1.1B, so the phone benefits too and it is computed once.
- **Clicking scans the whole list.** `TvPosterRows.kt:136` calls `state.items.indexOf(item)`
  — an O(n) scan per press. With rows carrying their own indices from the projection above,
  pass the index rather than searching for it.

### 1.3 — #008 the scroll wobble

**Verified cause.** `ChannelRow` (`app-tv/.../ui/live/TvLiveScreen.kt:149`) has no fixed
height, and `NowPlaying` (`TvLiveScreen.kt:256`) renders an **empty `Box`** when there is no
programme but a **two-line `Column`** — title plus progress bar — when there is. Guide data
arrives about 450 ms after focus settles on a row (`TvLiveScreen.kt:103`). So the row you are
resting on grows taller a beat after you stop on it, the `LazyColumn` relayouts, and
everything below shifts. Scroll down the list and it happens on row after row. That is the
wobble.

Give `ChannelRow` a fixed height and let `NowPlaying` fill reserved space rather than create
it — the empty case keeps the same footprint as the filled one.

**Second suspect, to confirm on the device rather than assume:** `Poster`
(`TvPosterRows.kt:245`) adds and removes `Modifier.basicMarquee()` on focus change, which
re-measures that node. If the fixed row height does not fully settle the Movies and Series
tabs, this is the next thing to look at.

### 1.4 — #003 the focused poster touching the category title

`CategoryRow` (`TvPosterRows.kt:151`) leaves 10 dp under the title. A poster is 150 dp wide
at a 2:3 ratio plus its label — roughly 253 dp tall — and focus scales the whole column by
`FOCUSED_SCALE = 1.1f` about its centre (`TvPosterRows.kt:193`), so it grows about **12.6 dp
past its own top edge**. More than the gap, which is why it touches.

Raise the title's bottom padding clear of the growth (16 dp or more) and add matching
vertical room inside the `LazyRow`, so the scaled card is not clipped by the row's own
bounds either.

### 1.5 — #009 the player

**The architectural half is clear and fixable now.** `TvApp` (`app-tv/.../ui/TvApp.kt:91`)
holds a `queue: List<Channel>` plus an index, and hands whatever is at that index to
`TvPlayerScreen`, which calls `viewModel.load(channel.id)` and nothing else
(`app-tv/.../ui/player/TvPlayerScreen.kt:101`).

`PlayerViewModel.load` (`feature/player/.../PlayerViewModel.kt:125`) already accepts
`customUrl`, `customTitle`, `startPositionMillis`, `seasonNumber` and `episodeNumber`. The
phone uses every one of them; the television uses none. Two consequences, each matching a
phrase in the bug report:

- **A series row has no playable stream.** Its `streamUrl` is not an episode — episodes come
  from `get_series_info` and are never rows in the channel table, which `Entities.kt:109`
  states explicitly. Pressing a series plays a non-stream. That is "shows series and movies
  as channels", literally.
- **A film gets the live treatment.** D-pad up and Channel-Up zap to another film
  (`handleKey`, `TvPlayerScreen.kt:245`); the controls print the live label whenever
  duration is 0 (`TvPlayerScreen.kt:359`); and no resume position is passed, so a film always
  restarts from zero even though `PlayerViewModel` would have resumed it.

Replace the `Channel`-plus-queue argument with an explicit request — live channel (with its
zap queue), film, or episode — and let `TvPlayerScreen` call the full `load(…)` signature.
Enable zap **only** for `LIVE`. Route series through the detail screen from 2.3 rather than
straight to playback.

**"Slow, glitchy, buggy" needs evidence before a fix is chosen, and the cause is not yet
known.** Two suspects to measure on the Haier: the `graphicsLayer` scale applied to the
`SurfaceView` (`TvPlayerScreen.kt:275`), which on some devices pushes a surface off its
direct composition path; and the `BufferMode` default on 1.84 GB of RAM and a 32-bit ABI.
Measure with `dumpsys gfxinfo` and logcat before changing either.

---

## Phase 2 — the missing television screens

`app-tv/build.gradle.kts:100` already depends on `:feature:browse`, `:feature:sources`,
`:feature:vod`, `:feature:series`, `:feature:player` and `:feature:settings`. **No module or
dependency changes are needed** — every ViewModel is already reachable. The phone screens
are phone-shaped Material 3 and want re-skinning for a 10-foot UI, but the ViewModels are
reused unchanged, per `PLAN-TV.md`: do not fork them.

### 2.0 — navigation

`TvApp` navigates by hand-rolled `remember` state, not Navigation-Compose. Extend that into
a small sealed `TvScreen` stack rather than adopting Navigation-Compose in `:app-tv`. The tab
bar's one-focusable model was rebuilt carefully to stop content changes stealing tab
selection (`TvApp.kt:174`), and a navigation library's own focus restoration is the most
likely thing to undo it.

### 2.1 — #002 categories in the Live list

`BrowseUiState.categories` and `selectCategory()` already exist and are already populated for
`LIVE` (`BrowseViewModel.kt:66`, `:198`, `:387`). `TvLiveScreen` simply never reads them. So
this is wiring plus a picker.

**Recommended shape: a focusable category rail down the left of the list.** On a remote that
is one left-press away and it keeps the channel-list shape the bug asks to preserve, where a
dropdown costs open, select, close. The bug offers a dropdown and invites a better
suggestion; this is the suggestion.

### 2.2 — #004 television settings

The gear in the top bar is **focusable with no `onClick` at all** — `BarIcon` takes no click
parameter (`TvApp.kt:251`, `:302`). A dead control, and the first thing to fix.

Reproduce every phone setting, from `SettingsViewModel`: category editing (kind selector,
hide, rename), the channel-logos toggle, TMDB key save and clear, seek interval, buffer mode,
max bitrate cap, theme mode, dynamic colour, backup export and import, and the licences
screen.

**This includes fixing the IME focus trap.** With the on-screen keyboard up the D-pad never
reaches the app, so every field typed lands in the same box. That blocks the TMDB key and
category rename here, and it is also one of the two open add-source bugs in
`TvSourcesScreen`. Route field-to-field movement through the IME's own next-field action
instead of raw D-pad keys, and fix both screens with it.

Backup export and import on a television also satisfies **AC-TV-07**, which is unrun.

### 2.3 — #005 and #007 television movie and series detail

`MovieDetailViewModel` and `SeriesDetailViewModel` are complete — `uiState`, `loadDetails()`,
`toggleFavorite()`, `removeFromHistory()`, `refreshResumePosition()`. Build television-shaped
screens over them: artwork, plot, rating, cast, favourite, and a play button that resumes.
Series additionally needs seasons and episodes, with an episode press calling the full
`load(…)` from 1.5.

This is what makes a series playable on a television at all, so #007 depends on 1.5. #005
should land first, as the simpler of the two.

### 2.4 — #006 the continue-watching row

`BrowseUiState.history` is already populated for non-Live, non-Favourites feeds
(`BrowseViewModel.kt:245`), and `channelForHistory()` (`BrowseViewModel.kt:263`) already
resolves an entry back to a playable row. The phone's `ContinueWatchingRow` is `internal` to
`:feature:browse`, so the television needs its own composable — which it would want anyway
for focus handling and 10-foot sizing. There is nothing to build in the data layer.

Place it as the first row above the category rows in `TvPosterRows`.

---

## Phase 3 — record the scope change

Write **Amendment 4** into `docs/FREEZE.md`, dated, in the established shape (Decision /
Rationale / What this costs / What does not change): the television gains detail screens, a
settings screen, category selection in Live, and a continue-watching row, and v1.0.0 does
not tag until they pass.

Then update `docs/ACCEPTANCE-SWEEP.md` §7 — new television screens mean new criteria and a
larger sweep — and add acceptance criteria to `docs/ACCEPTANCE.md` for TV settings and TV
detail, alongside the existing AC-TV-01…08.

---

## Verification

**The local gate is the only gate.** This repo has no git remote, so CI has never executed.
Run the full equivalent and treat it as authoritative:

```sh
./gradlew build detektAll coverageAll lint
```

Per phase:

- **1.1** — the new `ChannelDaoTest` and the off-main-dispatcher repository test. Then on
  device: load the 67k Xtream account, open Movies and Series, and compare against the
  pre-fix time. #010 is confirmed fixed only by flinging the Live list on the phone without
  an ANR.
- **1.3 and 1.4** — visual, on the television, with the remote only. Hold the D-pad down
  through the Live list: rows must not change height as guide data arrives.
- **1.5** — press a film and a series from the television catalogue. A film plays, seeks,
  resumes from its stored position, and does **not** zap on D-pad up. A series opens its
  detail screen rather than attempting playback.
- **Phase 2** — run **AC-TV-01**, **AC-TV-02** and **AC-TV-03** against every new screen
  (D-pad reaches every control; nothing is ever left unfocused; back lands on the category
  bar), and **AC-TV-07** via a backup restore on the television.
- **Panel budget** — after any change to browse or prefetch, re-check the sustained requests
  per second at the source, not by eye. `XtreamSourceTest` and `PanelRateLimiterTest` must
  stay green; they are what stops the provider block recurring.

Test with the D-pad only, and unplug any mouse — a mouse silently satisfies criteria a D-pad
would fail.

**Re-measure; do not carry figures over.** The Haier MatrixTV EE is `armeabi-v7a` with
1.84 GB of RAM and is the weakest hardware this project runs on. No phone number transfers.
