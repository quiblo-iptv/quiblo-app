**The Shelves of For You of Quiblo — two rankings, a light that reaches the corners, and rows that
are already there**

`020` built For You's three rows and left three questions open in its own last section. This round
answers two of them and adds the ones that arrived with a viewer using the screen: the popular row
is two rankings rather than one mixed shelf, a title the provider does not carry says so instead of
vanishing, both worked-out rows are remembered between opens, Recently Added is short enough to be
an answer, and a category can be put where its viewer wants it.

**Created:** 2026-08-16, against `5e18581` on `build/021-022-integration`.
**Ships as:** `1.0.0` work, `feat:`. A merge to `main` cuts a release; it is not merged until that
is asked for.

**Branch:** `feat/023-for-you-shelves`, on top of
`bugfix/BUG-031-hidden-scripts-leak-into-rows`.

The two rounds this one is cut away from are `024` — the scheduled work, the film details screen
and the resume race — and `025`, the recommender. They are separate because none of them is this,
and because this round leaves the app better whether or not either is built.

---

## The bug that went first

**Hiding a writing system did not reach either For You row, and that is BUG-031.** Every catalogue
feed hands the hidden mask to its query. The popular row and the suggestions row have no query to
hand it to — they choose their titles somewhere else and come to `ChannelRepository.channelsByIds`
only to turn ids into rows — so the filter was simply absent from the one code path where nobody
had thought to put it. A viewer who had hidden Arabic met Arabic titles in the two rows that
propose things unprompted, which is the worst place in the app for the setting to be ignored.

It shipped first, on its own branch, because it is four lines and it is the loudest thing on the
screen. A comment in `PopularTitlesRepository` asserted the filter applied; it is corrected rather
than deleted, because a comment that was wrong is worth a line saying what is true instead.

## The rows

| Row | Was | Is |
| :---- | :---- | :---- |
| **Recently added** | Forty titles, thirty-day window | Fifteen, same window |
| **Now popular** | One row: five films then five series, numbered 1–5 twice, badged | **Popular films** and **Popular series**, ten each, numbered once |
| **You may like** | Rebuilt from scratch on every open | Remembered, and appended to |

## Decisions, and what they rule out

**A place the provider cannot fill is still a place.** `020` intersected TMDB's list with the
catalogue and drew what survived, which read as a top ten with four films in it — and a viewer
could not tell whether the other six were unpopular or simply absent from their account. Every
place is now filled, and the ones the provider does not carry are dimmed, marked, and say **Not
available yet** when pressed. `FeedRowItem` is a sealed `Playable`/`Unavailable` pair rather than a
nullable channel, so there is no way to write a tile that claims to be playable and has no stream.

**This does not make Quiblo a content directory.** `FREEZE.md` §2 rules out a channel directory and
a "discover content" feature, and the distance from that space is a legal asset rather than an
accident. What keeps this on the right side of it is whose key the list comes from: the viewer's
own Movie Database key, against a service they signed up to. No key means no request, no cache and
no row — exactly as `020` built it. Quiblo indexes nothing, hosts nothing, and offers no way to
obtain anything it cannot already play.

**The cap is taken by rank, not after the match.** `020` took it after, so that a provider carrying
none of the top five still filled its row from further down. That was the right answer for a row
that hid its gaps and is the wrong one for a row that shows them: reaching to eleventh place to
cover a place the viewer cannot play would publish an ordering nobody measured.

**The numeral stands outside the poster, in a gutter of fixed width.** This is the most consequential
decision here and it is a deliberate reversal of `020`'s. That round put the rank *inside* the
artwork precisely so a tile's width could not depend on how many digits its number had — a tile
that measures differently from its neighbour is defect #008, the wobble that took four wrong
analyses. The shape asked for is the commercial one, with the figure beside the poster, and a
constant-width gutter is the one way to have it: `1` and `10` occupy the same rectangle, and the
figure is allowed to be clipped by the gutter rather than allowed to widen it.
`TvBrowseScrollStabilityTest` walks a twenty-four-tile ranked row, so it crosses 9 to 10 under the
remote, and a second case walks a ranked row with unavailable tiles in it. Both are flat.

**A ranked row reserves its caption line unconditionally.** That line is where "Not available yet"
is written, and a line that appeared on one tile when it was pressed would resize that tile in the
middle of a row. Same rule as `020`'s captions, applied to a row that has no captions of its own.

**Search says what light it wants; the shell draws it.** The drifting glow was a modifier on the
search screen's own `Column`, which sits inside the shell's 48dp content inset and below the tab
bar. `drawBehind` clips to the node it is on and the pools are sized as fractions of it, so the
light stopped dead at three padded edges, was cut off flat under the bar, and came out smaller than
the artwork light every other tab gets. It read as a lit rectangle rather than as light in a room.
`LocalAmbientSink` now carries an `AmbientRequest` — `None`, `Drift`, or `Artwork` — and the root
box draws whichever is asked for, full-bleed, exactly where the artwork light has always been
drawn.

**A ranking is replaced; suggestions are appended to.** Both rows are remembered in `feed_rows`,
and they are remembered differently on purpose. A ranking is one answer arrived at all at once, and
appending to last week's top ten would give a list of twenty in which the number means two
different things. Suggestions are the opposite: the same history scored twice a fortnight apart
returns mostly the same titles in a different order, and a shelf that reshuffles itself for no
visible reason is one nobody learns the shape of. So what is there stays where it is and new
suggestions arrive on the end.

**Two things leave the suggestions row.** A suggestion whose cause has been watched *again* since
it was made — a second viewing is the strongest signal this app collects, and leaving a
fortnight-old answer standing in front of what it produces would be ignoring it. And a suggestion
whose title the catalogue no longer has.

**The cache holds provider identities, never row ids.** A refresh deletes every row of a source and
reinserts it with a new autogenerated id. A remembered id would resolve to nothing the first time
anybody refreshed — and on the popular rows "resolves to nothing" is drawn as *unavailable*, so a
refresh would have produced a top ten claiming the provider carries none of it. The stable key is
what favourites and resume points already use, and for exactly this reason.

**Category order is the third local edit, keyed like the other two.** Hiding and renaming are
stored against the provider's own title, because that is the one thing that survives a refresh
reassigning every id. Ordering joins them on the same key. The column is nullable and that is the
design: "never moved" has to stay distinguishable from "moved to the front", because everything
unmoved falls back to the provider's order behind everything moved. A `NOT NULL DEFAULT 0` would
have declared every category on every existing installation to be in first place.

**Moving one category writes down where all of them sit.** Writing only the moved row would leave
it carrying a position while its neighbours carried none, and "is third before or after not-moved"
has no answer — so the first move would scatter the list rather than shift one shelf by one place.

**Buttons, not a drag.** On the phone a drag inside a list inside a scrolling settings page is two
gestures competing for one finger. On the television there is no drag to have, and the controls are
drawn only where there is somewhere to go: a control that takes a press and does nothing is worse
from three metres than one that is not there, because the viewer cannot see it is disabled and
presses it twice.

## What was built

| Layer | Change |
| :---- | :---- |
| `core/model` | `FeedRowId` and `FeedRowEntry` — the row identity moves here because it is now both what a screen draws and what a cached row is stored under; `Category.userOrder` |
| `core/database` | `FeedRowEntity` + `FeedRowDao`, schema **21**, `MIGRATION_20_21`; `CategoryOverrideEntity.userOrder`, schema **20**, `MIGRATION_19_20`; `ChannelDao.findAllByStableKeys`, `CategoryOverrideDao.upsertAll` |
| `core/data` | `FeedRowCacheRepository`; `PopularEntry` carries availability and TMDB's own title; `CategoryRepository.moveCategory`; `ChannelRepository.channelsByStableKeys`; `RecommendationRepository.lastWatchedByTitle`; the BUG-031 filter |
| `feature/browse` | `FeedRowItem` becomes sealed; `buildExtraRows` splits the rankings and writes through the cache; `RECENT_LIMIT` 40 → 15 |
| `feature/settings` | Move up and move down beside hide and rename |
| `app-tv` | `TvTile`; the rank gutter; the unavailable tile and its answer; `AmbientRequest` and the glow at the root; reorder chips in the category box |

## Acceptance criteria

1. Popular is two rows, films then series, at most ten each, numbered 1 upwards down each.
2. A popular title the provider does not carry keeps its place, is visibly unavailable, cannot be
   opened, and says so when pressed — without a dialog and without resizing anything.
3. Walking a ranked row past position 9 moves the screen vertically by nothing.
4. Both worked-out rows are on screen immediately on reopening, before anything is recomputed.
5. The suggestions row does not reorder itself between opens; new suggestions arrive at the end.
6. A suggestion whose cause has been watched again since is gone by the next build.
7. Search's light reaches all four screen edges and passes behind the tab bar.
8. Recently Added holds at most fifteen.
9. A category moved in Settings is moved on every tab that draws categories, on both apps, and
   stays moved across a refresh.
10. An existing installation upgrades through schema 19 → 21 without losing a channel, a favourite,
    a resume point or a category edit.
11. None of the popular row exists without the viewer's own metadata key.

## Testing

Automated, all on the JVM:

- `ChannelRepositoryTest` (+3) — BUG-031: a hidden-script title is absent from a row fetched by id,
  a row written before schema 19 is still decided by its name, and hiding nothing changes nothing.
  Both new cases fail without the fix.
- `PopularTitlesRepositoryTest` (rewritten, 3 cases) — an unmatched title keeps its place and its
  rank; ten of each are taken *by rank* rather than after the match; a provider carrying none of
  the list still gets the ranking, all of it unavailable.
- `FeedRowCacheTest` (new, 8) — a ranking is replaced; a ranking remembers the places it cannot
  fill; suggestions are appended and keep their places; a cause watched again drops its
  suggestions, and one watched *before* does not; a title the catalogue lost is dropped; the row
  does not grow past its cap; positions are always a run from zero.
- `RecommendationRepositoryTest` (new, 4) — silence with no history and with no described
  catalogue; hidden categories are never proposed out of; the last watch of each title is the
  latest across both kinds.
- `CategoryOrderTest` (new, 5) — the provider's order by default; moved first and the rest behind;
  one move writes the whole order; a rename or a hide survives a move; a move off either end is not
  a move.
- `TvBrowseScrollStabilityTest` (+1, and one rewritten) — the #008 guard, now walking the rank
  gutter across 9 to 10 and a ranked row holding unavailable tiles.
- `TvAmbientClearedTest` (+1) — Search asks the shell for its own light rather than drawing it.
- `MigrationTest` (+2) — 19 → 20 adds the category position unset, keeping the viewer's other
  category edits; 20 → 21 adds the remembered rows empty, keeping a channel and a resume point.
- `BrowseViewModelTest` — the popular row's identity follows the split, and the fifteen is asserted
  as a number rather than compared against itself.

**`:core:data` joins the coverage gate at Amendment 10's floor of 70, and stands at 74.7.** `020`
noted that `coverageAll` verified the two parser modules only and therefore said nothing about the
code that round wrote, and left that gap for the author's attention. This closes the half of it
that can be closed honestly: the matching, the scoring, the merge rules and the caches in
`:core:data` are pure functions over rows, which is the same reason the parsers are measured. The
UI modules are still excluded, for the same reason they always were — a covered line in a Compose
module measures how much of the framework got instantiated. The dependency-injection module is
excluded within `:core:data`: it is constructor calls with no branch in it, and executing it
measures Koin.

Manual: `docs/TESTING-REQUIRED.md` §A16, ten tickets. **A16.3 is the one that matters most** — it
is the #008 guard on a panel, and this round moved the numeral out of the artwork that defect made
it hide in. Rows two and three still cannot be certified from this machine (`docs/STOPPERS.md` S2,
no working Xtream account), and the unavailable tile additionally needs a provider that carries
less than TMDB's top ten, which is most of them.

## Open, and owned by the author

- **Whether the rank gutter is the right width.** 68dp is two digits at the size the figure is
  drawn, and it costs horizontal room: a ranked row shows fewer tiles per screen than a plain one.
  A panel is the only thing that can say whether that trade reads well.
- **Whether an unavailable tile should take focus at all.** It does, and it answers when pressed.
  Skipping it would be quieter but would leave gaps in the numbering with nothing to explain them.
- **Whether the phone gets For You.** Still scoped out, still `020`'s decision. The repositories
  and the cache are shared code; a phone screen would be UI work only.
- **Whether to merge.** The branch carries `feat:` commits, so a merge to `main` publishes a
  release.
