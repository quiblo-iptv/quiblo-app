**Recently Added and the Live Guide of Quiblo — what a provider did this week, and why the guide looks broken**

One request, two pieces of work, and they are unrelated to each other apart from arriving
together: a new television tab for the newest films and series, and a report that the guide does
not work for live channels.

**Created:** 2026-08-14, against `7eb4da0` on `main`.
**Ships as:** `1.0.0` work. The tab is `feat:`, the guide work is `fix:`; both cut a release on
merge, and neither is merged until that is asked for.

**Branches:** `feat/tv-recently-added`, `fix/tv-live-guide-blank-and-silent`.

---

## Part A — Recently Added

### The problem

Every catalogue tab on the television is ordered by the provider's own category list, and that
list does not change. Movies and Series are the same thousands of titles in the same order every
evening, so a viewer who has already watched what they wanted has no way to find out that their
provider added forty films last week — short of scrolling a catalogue they have already scrolled.

The panel knows. `get_vod_streams` carries `added` per film and `get_series` carries `added` or
`last_modified` per series, both unix seconds, both inside responses the app already makes on
every refresh. **The app parsed neither.** `VodStreamDto` and `SeriesDto` did not declare the
fields, `Channel` had no property for them, and `channels` had no column — so the feature is not
a screen. It is a field, a column, a migration, a query, and then a screen.

### Scope

- The date reaches the database, for Xtream sources only.
- One new television tab, between Live and Movies, holding one merged row newest first.
- An empty state that says which of the two reasons it is.

**Not in scope.** The phone app gains nothing here; the tab is the television's. No XMLTV, no
"new this week" badge on a poster elsewhere, no notification. A source that supplies no dates
gains no invented ordering.

### Decisions, and what they rule out

**One merged row rather than one row per kind.** Somebody asking what is new on their service is
not also choosing between films and series. On a 444dp panel — the real usable height after
overscan, see `docs/` and `012` — a second row sits below the fold anyway.

**Capped at 40** (`BrowseFeed.recentLimit`). A row is walked with a D-pad, and nobody presses
right forty times, let alone four hundred. This is a cap, not a page: nothing scrolls to load
more, because nothing reaches the end.

**The date is the provider's, never invented locally.** A "first time this app saw the title"
stamp would have worked for M3U too, and it was rejected: everything looks new on first import,
and a re-import resets it. A row that promises new arrivals and delivers import order is worse
than a row that says it cannot answer.

**`added` beats `last_modified` on a series.** `last_modified` moves when a cover is re-scraped
or a category renamed, neither of which is a new episode.

**A zero is not a date.** Panels send `"added": "0"` and empty strings. Both become null, because
downstream a zero is 1 January 1970 and sorts as a real date into a list ordered by one.

**`favoritesOnly: Boolean` became `BrowseScope`.** A third feed needed a third value, and two
booleans would have made "favourites and recently added at once" a state the type allowed and
every reader downstream had to be trusted never to produce. `TvBarSpot` in the television app is
the same decision, taken for the same reason, and written down there first.

### Acceptance criteria

1. The tab reads **Recently Added** and sits after Live and before Movies. Left and right reach
   it; Back from it returns to Search like every other tab.
2. On an Xtream source it holds films and series in one row, newest first, at most 40.
3. On an M3U source it says the playlist carries no dates — not "nothing here", and not an
   ordering invented from playlist position.
4. Opening a title from the row opens what the catalogue would have opened.
5. No additional request is made to the panel by any of the above.
6. An existing installation upgrades without losing a channel, a favourite or a resume point,
   and its dates fill in on the next refresh.

### What was built

| Layer | Change |
| :---- | :---- |
| `source/xtream` | `VodStreamDto.added`, `SeriesDto.added` / `last_modified`, `effectiveAddedEpochSeconds`, mapped to millis in `mapVod` / `mapSeries` |
| `core/model` | `Channel.addedAtEpochMillis: Long?` |
| `core/database` | `ChannelEntity.addedAtEpochMillis`, index `(sourceId, kind, addedAtEpochMillis)`, schema **16**, `MIGRATION_15_16`, `ChannelDao.observeRecentlyAdded` |
| `core/data` | `ChannelRepository.observeRecentlyAdded`, mappers both ways |
| `feature/browse` | `BrowseScope`, `BrowseFeed.scope` and `recentLimit`, `categoriesFor`, `recentlyAddedParams()` |
| `app-tv` | `TvTab.RECENTLY_ADDED`, `TvRecentlyAddedScreen`, three strings |

### Testing

Automated, all on the JVM:

- `XtreamDtoTest` — `added` as a string and as a number; absent, nonsense and zero all read as
  null; a series prefers `added` and falls back to `last_modified`.
- `XtreamSourceTest` — mapped films and series carry the date in millis; a panel that says
  nothing yields null rather than 1970.
- `RecentlyAddedQueryTest` (new, real Room on Robolectric) — newest first, live excluded,
  undated excluded, the cap takes the newest end, and a dateless source returns nothing.
- `MigrationTest` — 15 to 16 keeps every channel and dates none of them.
- `BrowseViewModelTest` — the newest-first feed reads only its own query and subscribes to no
  guide, no history and no categories; the catalogue feed still asks for its categories.
- `TvTabOrderTest` (new) — the bar's order, asserted rather than commented.

Manual: `docs/TESTING-REQUIRED.md` §A2.

---

## Part B — the guide on live channels

### The report

"EPG not working for live channels", on an Xtream panel.

### What the code actually does

The fetch and parse path is sound and covered: `get_short_epg` → `EpgResponse` → `toProgramme`
(base64-tolerant, seconds to millis, drops `end <= start`) → `programmes` → `observeNowPlaying`.
`XtreamSourceTest` exercises AC-EPG-01, -03 and -04, and `XtreamDtoTest` exercises the parse.
Nothing there is broken.

**Two things around it are.**

1. **The television's Live list opens blank and stays blank until the viewer moves the remote.**
   Guide data is fetched only for the row focus has rested on for 450ms
   (`TvLiveScreen.kt`, `FOCUS_SETTLE_MILLIS`). Nothing is focused when the screen opens, so no
   row carries a programme line. The phone does not behave this way — it fetches for the rows on
   screen. The restraint was deliberate and correct in origin: a held D-pad flies through rows
   far faster than a finger scrolls, and a request per row passed is how this project's account
   got blocked. But "one row at a time, after you stop" became "none at all, if you do not stop".

2. **Every guide failure is silent.** `ProviderBlocked` sets a fifteen-minute backoff and says
   nothing. A panel answering `epg_listings: []` — a channel with no `epg_channel_id`, which is
   common — says nothing. So a blank line means "not fetched yet", "your provider has no listing
   for this channel" and "your provider is refusing this app", and no viewer can tell which. This
   is `012`'s lesson again: *a screen is finished when its failure looks right, not when its
   success does.*

### Scope

- A bounded first-load prefetch on the television's Live list, inside the existing limiter and
  dedupe.
- A typed guide outcome that reaches the screen, and a sentence for the refusing case.

**Not in scope.** XMLTV for M3U sources. That is a whole feature — fetch `url-tvg`, gunzip, parse,
map to channels by `tvg-id`, cache — and it is the honest answer to "my M3U has no guide". It is
not this item.

### Acceptance criteria

1. Opening Live on the television fills the guide line for the first screenful of channels
   without the viewer pressing anything, within a second or two.
2. The number of guide requests made by opening Live is bounded and does not grow with the size
   of the account.
3. Scrolling still costs no requests, and resting on a row still costs at most one.
4. A panel refusing guide requests produces a sentence on screen, not a blank list.
5. A channel the panel has no listing for is distinguishable from one not yet asked about.

### Testing

Automated: a `ProviderBlocked` refresh surfaces the blocked state; the first-load prefetch issues
at most the bound and issues none on a second collection.

Manual: `docs/TESTING-REQUIRED.md` §A3. **Neither half can be certified from this machine** —
`docs/STOPPERS.md` S2 is still open and there is no working Xtream account here.

---

## Open, and owned by the author

- **Whether to merge.** Both branches carry `feat:` / `fix:` commits, so either merge to `main`
  publishes a release. Two branches are already finished and waiting on the same decision.
- **Whether an M3U guide is wanted at all.** If the answer is yes, XMLTV is its own round.
